package no.hal.risk.training;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import no.hal.risk.ai.Observation;

/**
 * Writes the decisions of a rollout for the learner, as arrays in an {@code .npz} file ({@link NpzWriter}),
 * with {@code S} steps, {@code N} territories, {@code F} features of a territory and {@code G} global features:
 * <ul>
 * <li>{@code obs} (S, N, F) and {@code globals} (S, G): what the network saw</li>
 * <li>{@code legal_offsets} (S + 1) and {@code legal_indices}: the positions of the legal actions of step {@code i}, which
 *   are {@code legal_indices[legal_offsets[i]:legal_offsets[i + 1]]}</li>
 * <li>{@code action} (S): the position of the action that was chosen</li>
 * <li>{@code log_prob} (S): the log of the probability it had, {@code value}: the value that was estimated</li>
 * <li>{@code advantage} and {@code value_target} (S): from the rewards, see {@link Rollout#estimateAdvantages}; {@code reward} (S)</li>
 * <li>{@code stats}: the numbers of games, truncated games, the average number of turns, the share of the games against bots that
 *   the network won (NaN if there were none), the number of opponents eliminated per game</li>
 * </ul>
 */
public class RolloutWriter {

  public static void write(Path path, Rollout rollout, int numTerritories, int nodeFeatures, int globalFeatures) throws IOException {
    var steps = rollout.steps();
    int count = steps.size();
    var observations = new float[count * numTerritories * nodeFeatures];
    var globals = new float[count * globalFeatures];
    var legalOffsets = new int[count + 1];
    int legalCount = 0;
    for (var step : steps) {
      legalCount += step.legal.length;
    }
    var legalIndices = new int[legalCount];
    var actions = new int[count];
    var logProbs = new float[count];
    var values = new float[count];
    var rewards = new float[count];
    var advantages = new float[count];
    var valueTargets = new float[count];
    int legalPosition = 0;
    for (int i = 0; i < count; i++) {
      var step = steps.get(i);
      copyObservation(step.observation, observations, i * numTerritories * nodeFeatures, numTerritories, nodeFeatures);
      System.arraycopy(step.observation.globals(), 0, globals, i * globalFeatures, globalFeatures);
      legalOffsets[i] = legalPosition;
      System.arraycopy(step.legal, 0, legalIndices, legalPosition, step.legal.length);
      legalPosition += step.legal.length;
      actions[i] = step.action;
      logProbs[i] = step.logProb;
      values[i] = step.value;
      rewards[i] = step.reward;
      advantages[i] = step.advantage;
      valueTargets[i] = step.valueTarget;
    }
    legalOffsets[count] = legalPosition;
    double[] stats = {rollout.games(), rollout.truncatedGames(), rollout.averageTurns(), rollout.winRateAgainstBots(), rollout.eliminationsPerGame()};
    try (var npz = new NpzWriter(Files.newOutputStream(path))) {
      npz.floats("obs", observations, count, numTerritories, nodeFeatures);
      npz.floats("globals", globals, count, globalFeatures);
      npz.ints("legal_offsets", legalOffsets, count + 1);
      npz.ints("legal_indices", legalIndices, legalCount);
      npz.ints("action", actions, count);
      npz.floats("log_prob", logProbs, count);
      npz.floats("value", values, count);
      npz.floats("reward", rewards, count);
      npz.floats("advantage", advantages, count);
      npz.floats("value_target", valueTargets, count);
      npz.doubles("stats", stats, stats.length);
    }
  }

  static void copyObservation(Observation observation, float[] target, int offset, int numTerritories, int nodeFeatures) {
    for (int i = 0; i < numTerritories; i++) {
      System.arraycopy(observation.territories()[i], 0, target, offset + i * nodeFeatures, nodeFeatures);
    }
  }

  /** The samples of imitation of the greedy bot, for the learner to learn from; see {@link Imitation.Kind} for the kinds. */
  public static void writeImitation(Path path, List<Imitation.Sample> samples, int numTerritories, int nodeFeatures, int globalFeatures) throws IOException {
    int count = samples.size();
    var observations = new float[count * numTerritories * nodeFeatures];
    var globals = new float[count * globalFeatures];
    var legalOffsets = new int[count + 1];
    var acceptableOffsets = new int[count + 1];
    int legalCount = 0, acceptableCount = 0;
    for (var sample : samples) {
      legalCount += sample.legal().length;
      acceptableCount += sample.acceptable().length;
    }
    var legalIndices = new int[legalCount];
    var acceptableIndices = new int[acceptableCount];
    var actions = new int[count];
    var kinds = new int[count];
    var valueTargets = new float[count];
    int legalPosition = 0, acceptablePosition = 0;
    for (int i = 0; i < count; i++) {
      var sample = samples.get(i);
      copyObservation(sample.observation(), observations, i * numTerritories * nodeFeatures, numTerritories, nodeFeatures);
      System.arraycopy(sample.observation().globals(), 0, globals, i * globalFeatures, globalFeatures);
      legalOffsets[i] = legalPosition;
      System.arraycopy(sample.legal(), 0, legalIndices, legalPosition, sample.legal().length);
      legalPosition += sample.legal().length;
      acceptableOffsets[i] = acceptablePosition;
      System.arraycopy(sample.acceptable(), 0, acceptableIndices, acceptablePosition, sample.acceptable().length);
      acceptablePosition += sample.acceptable().length;
      actions[i] = sample.action();
      kinds[i] = sample.kind().ordinal();
      valueTargets[i] = sample.valueTarget();
    }
    legalOffsets[count] = legalPosition;
    acceptableOffsets[count] = acceptablePosition;
    try (var npz = new NpzWriter(Files.newOutputStream(path))) {
      npz.floats("obs", observations, count, numTerritories, nodeFeatures);
      npz.floats("globals", globals, count, globalFeatures);
      npz.ints("legal_offsets", legalOffsets, count + 1);
      npz.ints("legal_indices", legalIndices, legalCount);
      npz.ints("acceptable_offsets", acceptableOffsets, count + 1);
      npz.ints("acceptable_indices", acceptableIndices, acceptableCount);
      npz.ints("action", actions, count);
      npz.ints("kind", kinds, count);
      npz.floats("value_target", valueTargets, count);
    }
  }
}
