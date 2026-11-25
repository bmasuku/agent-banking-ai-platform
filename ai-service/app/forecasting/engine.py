"""Statistical float forecaster.

Builds a recency-weighted hour-of-day profile of customer-driven net e-float and cash flows from the agent's
recent history, rolls it forward from current balances, and derives consumption metrics, exhaustion times and
replenishment needs. No model is trained; every number is a transparent statistic of the supplied history.
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

import numpy as np

from app.common.schemas import FeatureContribution
from app.common.stats import coefficient_of_variation, round_money, round_up_to
from app.forecasting.schemas import ForecastMetrics, ForecastRequest, PeakHour, TrajectoryPoint

DECAY = 0.85
Z80 = 1.2816
TOPUP_STEP = 50.0


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo is not None else dt.replace(tzinfo=UTC)


@dataclass
class ForecastResult:
    projected_efloat: float
    projected_cash: float
    min_efloat: float
    min_cash: float
    net_efloat: float
    net_cash: float
    stockout_efloat_h: float | None
    stockout_cash_h: float | None
    exhaustion_efloat_at: datetime | None
    exhaustion_cash_at: datetime | None
    topup_efloat: float
    topup_cash: float
    risk: str
    metrics: ForecastMetrics
    confidence: float
    confidence_basis: str
    trajectory: list[TrajectoryPoint]
    drivers: list[FeatureContribution]
    explanation: str


def forecast(req: ForecastRequest) -> ForecastResult:
    zone = ZoneInfo(req.timezone)
    as_of = _aware(req.as_of)
    today = as_of.astimezone(zone).date()

    by_day: dict = {}
    for flow in req.history:
        local = _aware(flow.hour).astimezone(zone)
        day = local.date()
        if day >= today or (today - day).days > req.lookback_days:
            continue
        cells = by_day.setdefault(day, np.zeros((24, 3)))
        cells[local.hour] += (flow.efloat_net, flow.cash_net, flow.transaction_count)

    observed_days = 0 if not by_day else min(req.lookback_days, (today - min(by_day)).days)
    days = [today - timedelta(days=d) for d in range(1, observed_days + 1)]
    cube = np.stack([by_day.get(d, np.zeros((24, 3))) for d in days]) if days else np.zeros((0, 24, 3))

    if observed_days:
        weights = np.array([DECAY ** i for i in range(observed_days)])
        w = weights / weights.sum()
        mean = np.tensordot(w, cube, axes=1)
        var = np.tensordot(w, (cube - mean) ** 2, axes=1)
    else:
        mean = np.zeros((24, 3))
        var = np.zeros((24, 3))

    metrics = _metrics(cube, mean, observed_days)

    efloat, cash = req.current_efloat, req.current_cash
    min_e, min_c = efloat, cash
    var_e = var_c = 0.0
    var_e_at_min = var_c_at_min = 0.0
    stockout_e = 0.0 if efloat < req.min_efloat_threshold else None
    stockout_c = 0.0 if cash < req.min_cash_threshold else None
    exhaust_e = as_of if efloat <= 0 else None
    exhaust_c = as_of if cash <= 0 else None
    start = as_of.replace(minute=0, second=0, microsecond=0)
    trajectory: list[TrajectoryPoint] = []
    for t in range(1, req.horizon_hours + 1):
        point = start + timedelta(hours=t)
        hour = (point - timedelta(hours=1)).astimezone(zone).hour
        efloat += mean[hour, 0]
        cash += mean[hour, 1]
        var_e += var[hour, 0]
        var_c += var[hour, 1]
        if efloat < min_e:
            min_e, var_e_at_min = efloat, var_e
        if cash < min_c:
            min_c, var_c_at_min = cash, var_c
        if stockout_e is None and efloat < req.min_efloat_threshold:
            stockout_e = float(t)
        if stockout_c is None and cash < req.min_cash_threshold:
            stockout_c = float(t)
        if exhaust_e is None and efloat <= 0:
            exhaust_e = point
        if exhaust_c is None and cash <= 0:
            exhaust_c = point
        band_e, band_c = Z80 * math.sqrt(var_e), Z80 * math.sqrt(var_c)
        trajectory.append(TrajectoryPoint(hour=point, efloat=round_money(efloat), efloat_lower=round_money(efloat - band_e),
                                          efloat_upper=round_money(efloat + band_e), cash=round_money(cash),
                                          cash_lower=round_money(cash - band_c), cash_upper=round_money(cash + band_c)))

    topup_e = round_up_to(req.min_efloat_threshold - (min_e - Z80 * math.sqrt(var_e_at_min)), TOPUP_STEP)
    topup_c = round_up_to(req.min_cash_threshold - (min_c - Z80 * math.sqrt(var_c_at_min)), TOPUP_STEP)
    short_e, short_c = min_e < req.min_efloat_threshold, min_c < req.min_cash_threshold
    risk = "BOTH_SHORTAGE" if short_e and short_c else "EFLOAT_SHORTAGE" if short_e else "CASH_SHORTAGE" if short_c else "NONE"

    daily_counts = cube[:, :, 2].sum(axis=1) if observed_days else np.zeros(0)
    cv = coefficient_of_variation(daily_counts.tolist())
    confidence = 0.2 if observed_days == 0 else min(0.9, 0.35 + 0.04 * observed_days) * (1 - min(0.3, 0.1 * cv))

    net_e, net_c = efloat - req.current_efloat, cash - req.current_cash
    drivers = _drivers(req.horizon_hours, net_e, net_c, metrics, observed_days)
    explanation = _explain(req, net_e, net_c, efloat, cash, stockout_e, stockout_c, exhaust_e, exhaust_c, topup_e, topup_c,
                           metrics, observed_days)
    return ForecastResult(
        projected_efloat=round_money(efloat), projected_cash=round_money(cash), min_efloat=round_money(min_e),
        min_cash=round_money(min_c), net_efloat=round_money(net_e), net_cash=round_money(net_c),
        stockout_efloat_h=stockout_e, stockout_cash_h=stockout_c, exhaustion_efloat_at=exhaust_e, exhaustion_cash_at=exhaust_c,
        topup_efloat=topup_e, topup_cash=topup_c, risk=risk, metrics=metrics, confidence=round(confidence, 4),
        confidence_basis="Days of history (more is better) reduced by day-to-day variability of transaction counts",
        trajectory=trajectory, drivers=drivers, explanation=explanation)


def _metrics(cube: np.ndarray, mean: np.ndarray, observed_days: int) -> ForecastMetrics:
    if observed_days == 0:
        return ForecastMetrics(observed_days=0, avg_hourly_efloat_consumption=0, avg_hourly_cash_consumption=0,
                               avg_daily_efloat_consumption=0, avg_daily_cash_consumption=0, avg_daily_efloat_net_change=0,
                               avg_daily_cash_net_change=0, transactions_per_day=0, transactions_per_active_hour=0,
                               peak_hours=[], peak_efloat_outflow_hour=None, peak_cash_outflow_hour=None)
    efloat_out = np.clip(-cube[:, :, 0], 0, None)
    cash_out = np.clip(-cube[:, :, 1], 0, None)
    counts = cube[:, :, 2]
    active_hours = int((counts > 0).sum()) or int((np.abs(cube[:, :, :2]).sum(axis=2) > 0).sum())
    total_tx = float(counts.sum())

    peaks = sorted(range(24), key=lambda h: mean[h, 2], reverse=True)
    peak_hours = [PeakHour(hour_of_day=h, avg_transactions=round(float(mean[h, 2]), 2), avg_efloat_net=round_money(mean[h, 0]),
                           avg_cash_net=round_money(mean[h, 1])) for h in peaks[:3] if mean[h, 2] > 0]
    worst_e = int(np.argmin(mean[:, 0]))
    worst_c = int(np.argmin(mean[:, 1]))
    return ForecastMetrics(
        observed_days=observed_days,
        avg_hourly_efloat_consumption=round_money(efloat_out.sum() / active_hours) if active_hours else 0.0,
        avg_hourly_cash_consumption=round_money(cash_out.sum() / active_hours) if active_hours else 0.0,
        avg_daily_efloat_consumption=round_money(efloat_out.sum() / observed_days),
        avg_daily_cash_consumption=round_money(cash_out.sum() / observed_days),
        avg_daily_efloat_net_change=round_money(cube[:, :, 0].sum() / observed_days),
        avg_daily_cash_net_change=round_money(cube[:, :, 1].sum() / observed_days),
        transactions_per_day=round(total_tx / observed_days, 2),
        transactions_per_active_hour=round(total_tx / active_hours, 2) if active_hours else 0.0,
        peak_hours=peak_hours,
        peak_efloat_outflow_hour=worst_e if mean[worst_e, 0] < 0 else None,
        peak_cash_outflow_hour=worst_c if mean[worst_c, 1] < 0 else None)


def _drivers(horizon: int, net_e: float, net_c: float, m: ForecastMetrics, days: int) -> list[FeatureContribution]:
    drivers = [
        FeatureContribution(feature="predictedEfloatNetFlow", value=f"{net_e:.2f}", contribution=round_money(net_e),
                            direction="DRAINS_EFLOAT" if net_e < 0 else "BUILDS_EFLOAT",
                            description=f"Expected net e-float movement over the next {horizon}h from same-hour history"),
        FeatureContribution(feature="predictedCashNetFlow", value=f"{net_c:.2f}", contribution=round_money(net_c),
                            direction="DRAINS_CASH" if net_c < 0 else "BUILDS_CASH",
                            description=f"Expected net cash movement over the next {horizon}h"),
        FeatureContribution(feature="avgHourlyEfloatConsumption", value=f"{m.avg_hourly_efloat_consumption:.2f}",
                            contribution=-m.avg_hourly_efloat_consumption, direction="DRAINS_EFLOAT",
                            description="Average e-float paid out per active trading hour"),
        FeatureContribution(feature="transactionsPerDay", value=f"{m.transactions_per_day:.2f}",
                            contribution=m.transactions_per_day, direction="ACTIVITY",
                            description="Average customer transactions per day"),
    ]
    if m.peak_hours:
        p = m.peak_hours[0]
        drivers.append(FeatureContribution(feature="peakActivityHour", value=f"{p.hour_of_day:02d}:00",
                                           contribution=p.avg_transactions, direction="ACTIVITY",
                                           description=f"Busiest hour is around {p.hour_of_day:02d}:00 "
                                                       f"({p.avg_transactions:.1f} transactions on average)"))
    drivers.append(FeatureContribution(feature="historyDays", value=str(days), contribution=days, direction="CONFIDENCE",
                                       description=f"{days} day(s) of hourly history used"))
    return drivers


def _explain(req: ForecastRequest, net_e: float, net_c: float, efloat: float, cash: float, stockout_e: float | None,
             stockout_c: float | None, exhaust_e: datetime | None, exhaust_c: datetime | None, topup_e: float,
             topup_c: float, m: ForecastMetrics, days: int) -> str:
    text = (f"Over the next {req.horizon_hours}h e-float is expected to move by {net_e:,.2f} (to {efloat:,.2f}) and cash by "
            f"{net_c:,.2f} (to {cash:,.2f}), based on recency-weighted hour-of-day patterns from {days} day(s) of history. "
            f"Typical consumption: {m.avg_daily_efloat_consumption:,.2f} e-float and {m.avg_daily_cash_consumption:,.2f} cash "
            f"per day, about {m.transactions_per_day:.1f} transactions per day.")
    if stockout_e is not None:
        text += (f" E-float is projected to fall below the minimum operating level of {req.min_efloat_threshold:,.2f} "
                 + ("already." if stockout_e == 0 else f"in about {int(stockout_e)}h."))
    if stockout_c is not None:
        text += (f" Cash is projected to fall below the minimum operating level of {req.min_cash_threshold:,.2f} "
                 + ("already." if stockout_c == 0 else f"in about {int(stockout_c)}h."))
    if exhaust_e is not None:
        text += f" E-float is projected to run out around {exhaust_e.astimezone(ZoneInfo(req.timezone)):%Y-%m-%d %H:%M}."
    if exhaust_c is not None:
        text += f" Cash is projected to run out around {exhaust_c.astimezone(ZoneInfo(req.timezone)):%Y-%m-%d %H:%M}."
    if topup_e > 0 or topup_c > 0:
        parts = []
        if topup_e > 0:
            parts.append(f"e-float {topup_e:,.0f}")
        if topup_c > 0:
            parts.append(f"cash {topup_c:,.0f}")
        text += " Recommended replenishment (with an 80% safety buffer): " + " and ".join(parts) + "."
    else:
        text += " No replenishment needed within the horizon."
    return text
