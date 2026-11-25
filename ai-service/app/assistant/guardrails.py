"""Refuse requests that ask the assistant to act, not just inform."""
from __future__ import annotations

import re

# Phrases that ask the assistant to perform a prohibited action. Informational questions
# ("what is my balance?", "how do I request an advance?") are allowed.
_PROHIBITED: list[tuple[str, str]] = [
    (r"\b(send|transfer|deposit|withdraw|cash[\s-]?in|cash[\s-]?out|disburse)\b.{0,40}\b(money|cash|efloat|e-?float|funds|amount)\b",
     "execute a transaction"),
    (r"\b(approve|reject|decline)\b.{0,40}\b(advance|loan|request|disbursement)\b",
     "approve or reject an advance"),
    (r"\b(change|update|set|adjust|modify)\b.{0,40}\b(balance|efloat|e-?float|cash|limit)\b",
     "change balances or limits"),
    (r"\b(classify|mark|confirm|clear|flag)\b.{0,40}\b(fraud|transaction|alert)\b",
     "classify fraud"),
    (r"\b(freeze|suspend|block|unfreeze|reactivate)\b.{0,40}\b(agent|account)\b",
     "change an agent's status"),
    (r"\b(please\s+)?(do|make|perform|execute|process)\b.{0,30}\b(transaction|transfer|payment|replenishment|exchange)\b",
     "execute a financial action"),
]

_COMPILED = [(re.compile(p, re.IGNORECASE | re.DOTALL), label) for p, label in _PROHIBITED]

REFUSAL_TEMPLATE = (
    "I can only retrieve and explain authorised information. I cannot {action}. "
    "Please use the corresponding action screen in the app or portal, where the "
    "backend will enforce permissions and record the ledger entry."
)


def check_prohibited(question: str) -> str | None:
    """Return a refusal message when the question asks for a prohibited action, else None."""
    text = question.strip()
    for pattern, action in _COMPILED:
        if pattern.search(text):
            return REFUSAL_TEMPLATE.format(action=action)
    return None
