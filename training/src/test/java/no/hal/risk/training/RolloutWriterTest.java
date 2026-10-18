package no.hal.risk.training;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import no.hal.risk.ai.Observation;
import org.junit.jupiter.api.Test;

public class RolloutWriterTest {

  /** An observation of 3 territories with 2 features, and 4 global features, with numbers that tell where they are. */
  private static Observation observation(int seed) {
    var territories = new float[3][2];
    for (int i = 0; i < 3; i++) {
      for (int f = 0; f < 2; f++) {
        territories[i][f] = seed * 100 + i * 10 + f;
      }
    }
    return new Observation(territories, new float[] {seed, seed + 0.5f, seed + 0.25f, -seed});
  }

  @Test
  public void testTheDecisionsAreWrittenAsArraysOfOneEntryForEachStep() throws IOException {
    var rollout = new Rollout();
    var first = new Rollout.Step(observation(1), new int[] {4, 7, 9}, 7, -0.5f, 0.25f);
    first.reward = 1.5f;
    first.advantage = 0.75f;
    first.valueTarget = 2f;
    var second = new Rollout.Step(observation(2), new int[] {3}, 3, 0f, -1f);
    var third = new Rollout.Step(observation(3), new int[] {0, 1}, 0, -2f, 0.5f);
    rollout.steps().addAll(List.of(first, second, third));
    rollout.addGame(30, false, true, true, 2);
    rollout.addGame(50, true, true, false, 0);
    var file = Files.createTempFile("riskfx", ".npz");
    RolloutWriter.write(file, rollout, 3, 2, 4);
    var arrays = NpzReader.read(file);

    assertArrayEquals(new int[] {3, 3, 2}, arrays.get("obs").shape());
    assertArrayEquals(new int[] {3, 4}, arrays.get("globals").shape());
    // the second state, the third territory, the second feature
    assertEquals(2 * 100 + 2 * 10 + 1, arrays.get("obs").floats()[1 * 6 + 2 * 2 + 1]);
    assertEquals(2.5f, arrays.get("globals").floats()[1 * 4 + 1]);
    assertArrayEquals(new int[] {0, 3, 4, 6}, arrays.get("legal_offsets").ints());
    assertArrayEquals(new int[] {4, 7, 9, 3, 0, 1}, arrays.get("legal_indices").ints());
    assertArrayEquals(new int[] {7, 3, 0}, arrays.get("action").ints());
    assertArrayEquals(new float[] {-0.5f, 0f, -2f}, arrays.get("log_prob").floats());
    assertArrayEquals(new float[] {0.25f, -1f, 0.5f}, arrays.get("value").floats());
    assertArrayEquals(new float[] {1.5f, 0f, 0f}, arrays.get("reward").floats());
    assertArrayEquals(new float[] {0.75f, 0f, 0f}, arrays.get("advantage").floats());
    assertArrayEquals(new float[] {2f, 0f, 0f}, arrays.get("value_target").floats());
    // games, truncated games, average turns, the share won against bots, eliminations per game
    assertArrayEquals(new double[] {2, 1, 40, 0.5, 1}, arrays.get("stats").doubles());
    Files.delete(file);
  }

  @Test
  public void testARolloutWithoutStepsIsWrittenWithEmptyArrays() throws IOException {
    var file = Files.createTempFile("riskfx", ".npz");
    RolloutWriter.write(file, new Rollout(), 3, 2, 4);
    var arrays = NpzReader.read(file);
    assertArrayEquals(new int[] {0, 3, 2}, arrays.get("obs").shape());
    assertArrayEquals(new int[] {0}, arrays.get("legal_offsets").ints());
    assertTrue(Double.isNaN(arrays.get("stats").doubles()[3]));
    Files.delete(file);
  }

  @Test
  public void testImitationSamplesAreWrittenWithTheirAcceptableActions() throws IOException {
    var samples = new ArrayList<Imitation.Sample>();
    samples.add(new Imitation.Sample(observation(1), new int[] {5, 6}, 5, new int[] {5, 6}, Imitation.Kind.ATTACK, 0.5f));
    samples.add(new Imitation.Sample(observation(2), new int[] {2}, 2, new int[] {2}, Imitation.Kind.END_ATTACK, -1f));
    var file = Files.createTempFile("riskfx", ".npz");
    RolloutWriter.writeImitation(file, samples, 3, 2, 4);
    var arrays = NpzReader.read(file);
    assertArrayEquals(new int[] {2, 3, 2}, arrays.get("obs").shape());
    assertArrayEquals(new int[] {0, 2, 3}, arrays.get("legal_offsets").ints());
    assertArrayEquals(new int[] {0, 2, 3}, arrays.get("acceptable_offsets").ints());
    assertArrayEquals(new int[] {5, 6, 2}, arrays.get("acceptable_indices").ints());
    assertArrayEquals(new int[] {Imitation.Kind.ATTACK.ordinal(), Imitation.Kind.END_ATTACK.ordinal()}, arrays.get("kind").ints());
    assertArrayEquals(new float[] {0.5f, -1f}, arrays.get("value_target").floats());
    Files.delete(file);
  }
}
