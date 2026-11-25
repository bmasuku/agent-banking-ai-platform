"""Explainable 0-1000 credit scorecard.

Every adjustment to the base score is reported as a factor. The points are expert-set and mirrored exactly by the
Spring Boot fallback scorer (``RuleBasedCreditScorer``); they are not fitted to default outcomes.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from app.common.schemas import FeatureContribution
from app.credit.schemas import CreditRequest

BASE_SCORE = 500
APPROVE_MIN_SCORE = 720
BAND_CAPS = {"LOW": 5000.0, "MEDIUM": 2500.0, "HIGH": 1000.0, "CRITICAL": 0.0}
VOLUME_FACTOR = 0.25
LIMIT_STEP = 50.0


def jround(x: float) -> int:
    """Half-up rounding, identical to Java's Math.round."""
    return int(math.floor(x + 0.5))


def band_for(score: int) -> str:
    if score >= 750:
        return "LOW"
    if score >= 600:
        return "MEDIUM"
    if score >= 450:
        return "HIGH"
    return "CRITICAL"


def probability_of_default(score: int) -> float:
    return round(1.0 / (1.0 + math.exp((score - 600) / 90.0)), 4)


@dataclass
class CreditResult:
    score: int
    band: str
    pd: float
    recommended_limit: float
    recommended_advance: float
    eligible: bool
    decision: str
    reasons: list[str]
    factors: list[FeatureContribution]
    explanation: str
    confidence: float
    confidence_basis: str


def score_factors(f: CreditRequest) -> list[FeatureContribution]:
    factors: list[FeatureContribution] = []

    def add(feature: str, value: object, points: int, description: str) -> None:
        if points != 0:
            factors.append(FeatureContribution(feature=feature, value=str(value), contribution=points,
                                               direction="POSITIVE" if points > 0 else "NEGATIVE", description=description))

    if f.tenure_days < 30:
        add("accountAgeDays", f.tenure_days, -20, f"New agent account ({f.tenure_days} days old)")
    else:
        add("accountAgeDays", f.tenure_days, min(80, jround(f.tenure_days / 365.0 * 80)),
            f"Agent account is {f.tenure_days} days old")

    add("activeDays30d", f.active_days_30d, jround(min(30, f.active_days_30d) / 30.0 * 80),
        f"Active on {f.active_days_30d} of the last 30 days")

    volume = f.transaction_volume_30d
    add("transactionVolume30d", f"{volume:.2f}", jround(max(0.0, min(100.0, (math.log10(volume + 1) - 2) * 40))),
        f"30-day customer transaction volume of {volume:,.2f}")

    add("avgDailyCommission30d", f"{f.avg_daily_commission_30d:.2f}", jround(min(80.0, f.avg_daily_commission_30d * 8)),
        f"Average commission earnings of {f.avg_daily_commission_30d:,.2f} per day")

    if f.active_days_30d >= 5:
        cv = f.commission_volatility
        points = 30 if cv < 0.5 else 10 if cv < 1.0 else -20
        add("commissionVolatility", f"{cv:.2f}", points,
            "Stable daily commission income" if points > 0 else "Highly variable daily commission income")

    on_time = max(0, f.advances_repaid - f.advances_repaid_late)
    add("advancesRepaidOnTime", on_time, min(100, on_time * 25), f"{on_time} float advance(s) repaid on time")
    add("advancesRepaidLate", f.advances_repaid_late, -min(120, f.advances_repaid_late * 40),
        f"{f.advances_repaid_late} advance(s) repaid after the due date")
    add("advancesDefaulted", f.advances_defaulted, -min(300, f.advances_defaulted * 150),
        f"{f.advances_defaulted} previous default(s) (more than 30 days past due)")
    add("advancesOverdue", f.advances_overdue, -min(200, f.advances_overdue * 100),
        f"{f.advances_overdue} advance(s) currently overdue")

    if f.outstanding_principal > 0:
        leverage = f.outstanding_principal / max(1.0, volume / 30.0)
        points = -60 if leverage > 5 else -30 if leverage > 2 else 0
        add("outstandingPrincipal", f"{f.outstanding_principal:.2f}", points,
            f"Outstanding advance principal equals {leverage:.1f} days of average transaction volume")

    if f.outstanding_interest > 0:
        cover_days = f.outstanding_interest / max(0.01, f.avg_daily_commission_30d)
        points = -40 if cover_days > 10 else -15 if cover_days > 3 else 0
        add("outstandingInterest", f"{f.outstanding_interest:.2f}", points,
            f"Outstanding interest equals {cover_days:.1f} days of commission earnings")

    add("confirmedFraudCount", f.confirmed_fraud_count, -min(400, f.confirmed_fraud_count * 200),
        f"{f.confirmed_fraud_count} confirmed fraud case(s)")
    add("flaggedTransactions30d", f.flagged_transactions_30d, -min(90, f.flagged_transactions_30d * 15),
        f"{f.flagged_transactions_30d} transaction(s) flagged by fraud monitoring in 30 days")
    add("openInvestigations", f.open_investigations, -min(160, f.open_investigations * 80),
        f"{f.open_investigations} open risk investigation(s)")

    status = f.agent_status.upper()
    status_points = -60 if status == "UNDER_REVIEW" else -250 if status in {"FROZEN", "SUSPENDED"} else 0
    add("agentStatus", status, status_points, f"Agent status is {status}")
    return factors


def evaluate(f: CreditRequest) -> CreditResult:
    factors = score_factors(f)
    score = max(0, min(1000, BASE_SCORE + sum(int(c.contribution) for c in factors)))
    band = band_for(score)

    capacity = f.transaction_volume_30d * VOLUME_FACTOR
    limit = min(BAND_CAPS[band], capacity)
    limit = math.floor(limit / LIMIT_STEP) * LIMIT_STEP if limit > 0 else 0.0
    advance = max(0.0, limit - f.outstanding_principal)
    advance = math.floor(advance / LIMIT_STEP) * LIMIT_STEP if advance > 0 else 0.0

    status = f.agent_status.upper()
    rejections: list[str] = []
    if status in {"FROZEN", "SUSPENDED"}:
        rejections.append(f"Agent status {status} does not permit credit")
    if f.confirmed_fraud_count > 0:
        rejections.append("Confirmed fraud on record")
    if f.advances_defaulted > 0:
        rejections.append("Previous default on a float advance")
    if f.advances_overdue > 0:
        rejections.append("An existing advance is overdue and must be repaid first")
    if band == "CRITICAL":
        rejections.append(f"Credit score {score} is in the CRITICAL band (below 450)")
    if advance <= 0 and not rejections:
        rejections.append("No available credit capacity after outstanding principal")

    if rejections:
        decision, reasons = "REJECTED", rejections
    else:
        review: list[str] = []
        if score < APPROVE_MIN_SCORE:
            review.append(f"Credit score {score} is below the automatic approval level of {APPROVE_MIN_SCORE}")
        if f.open_investigations > 0:
            review.append("Open risk investigation")
        if status != "ACTIVE":
            review.append(f"Agent status is {status}")
        if f.flagged_transactions_30d > 0:
            review.append("Recent fraud alerts need human review")
        if f.requested_amount is not None and f.requested_amount > advance:
            review.append(f"Requested {f.requested_amount:,.2f} exceeds the recommended advance of {advance:,.2f}")
        if review:
            decision, reasons = "MANUAL_REVIEW", review
        else:
            decision, reasons = "APPROVED", ["All scorecard eligibility conditions are met"]

    has_repayment_history = (f.advances_repaid + f.advances_repaid_late + f.advances_overdue + f.advances_defaulted) > 0
    confidence = 0.5 + 0.15 * (f.tenure_days >= 90) + 0.15 * (f.active_days_30d >= 15) + 0.15 * has_repayment_history

    ordered = sorted(factors, key=lambda c: abs(c.contribution), reverse=True)
    strengths = "; ".join([c.description for c in ordered if c.contribution > 0][:3])
    concerns = "; ".join([c.description for c in ordered if c.contribution < 0][:3])
    explanation = f"Score {score}/1000 ({band} risk) from a base of {BASE_SCORE}."
    if strengths:
        explanation += f" Strengths: {strengths}."
    if concerns:
        explanation += f" Concerns: {concerns}."
    explanation += f" Recommendation: {decision} - {'; '.join(reasons)}."

    return CreditResult(score=score, band=band, pd=probability_of_default(score), recommended_limit=float(limit),
                        recommended_advance=float(advance), eligible=decision != "REJECTED", decision=decision,
                        reasons=reasons, factors=ordered, explanation=explanation, confidence=round(confidence, 4),
                        confidence_basis="Data sufficiency: account age >= 90 days, >= 15 active days, repayment history")
