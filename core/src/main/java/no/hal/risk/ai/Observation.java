package no.hal.risk.ai;

/**
 * What the network sees: a feature row for each territory, and some features for the whole board.
 * The names of the features are given by {@link ObservationEncoder}.
 *
 * @param territories one row for each territory, in the order of RiskEnv.territories()
 * @param globals features of the whole board and the turn
 */
public record Observation(float[][] territories, float[] globals) {
}
