from __future__ import annotations

import math
from collections.abc import Sequence

import numpy as np
from scipy import stats as sps

MAD_SCALE = 0.6745


def robust_z(value: float, sample: Sequence[float]) -> float:
    """Modified z-score (Iglewicz & Hoaglin) using the median and median absolute deviation."""
    arr = np.asarray(sample, dtype=float)
    if arr.size == 0:
        return 0.0
    median = float(np.median(arr))
    mad = float(np.median(np.abs(arr - median)))
    if mad == 0:
        mean_ad = float(np.mean(np.abs(arr - median)))
        if mean_ad == 0:
            return 0.0 if value == median else math.copysign(10.0, value - median)
        return (value - median) / (1.2533 * mean_ad)
    return MAD_SCALE * (value - median) / mad


def poisson_upper_tail(observed: int, expected: float) -> float:
    """P(X >= observed) for X ~ Poisson(expected)."""
    if observed <= 0:
        return 1.0
    return float(sps.poisson.sf(observed - 1, max(expected, 1e-9)))


def binomial_upper_tail(successes: int, trials: int, rate: float) -> float:
    """P(X >= successes) for X ~ Binomial(trials, rate)."""
    if successes <= 0 or trials <= 0:
        return 1.0
    return float(sps.binom.sf(successes - 1, trials, min(max(rate, 1e-6), 1.0)))


def coefficient_of_variation(values: Sequence[float]) -> float:
    arr = np.asarray(values, dtype=float)
    if arr.size < 2:
        return 0.0
    mean = float(np.mean(np.abs(arr)))
    return 0.0 if mean == 0 else float(np.std(arr) / mean)


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def round_money(value: float) -> float:
    return float(round(value + 0.0, 2))


def round_up_to(value: float, step: float) -> float:
    if value <= 0:
        return 0.0
    return float(math.ceil(value / step) * step)


def round_down_to(value: float, step: float) -> float:
    if value <= 0:
        return 0.0
    return float(math.floor(value / step + 1e-9) * step)
