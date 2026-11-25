package com.agentbanking.platform.ai;

/**
 * Explainability unit shared by all AI integrations: how much one input feature moved the outcome.
 *
 * @param contribution signed effect on the output (probability points, score points or currency)
 * @param direction    e.g. INCREASES_RISK, DECREASES_RISK, POSITIVE, NEGATIVE, DRIVES_DEMAND
 */
public record FeatureContribution(String feature, String value, double contribution, String direction, String description) {
}
