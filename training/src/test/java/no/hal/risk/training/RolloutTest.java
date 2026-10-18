package no.hal.risk.training;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class RolloutTest {

  private static Rollout.Step step(float reward, float value) {
    var step = new Rollout.Step(null, new int[0], 0, 0, value);
    step.reward = reward;
    return step;
  }

  @Test
  public void testAdvantagesByHand() {
    var trajectory = List.of(step(0, 0.5f), step(0, 0.2f), step(1, 0.1f));
    Rollout.estimateAdvantages(trajectory, 0.9, 0.5);
    assertEquals(-0.18725, trajectory.get(0).advantage, 1e-6);
    assertEquals(0.295, trajectory.get(1).advantage, 1e-6);
    assertEquals(0.9, trajectory.get(2).advantage, 1e-6);
    assertEquals(0.5 - 0.18725, trajectory.get(0).valueTarget, 1e-6);
    assertEquals(1.0, trajectory.get(2).valueTarget, 1e-6);
  }

  @Test
  public void testAdvantagesWithoutDiscountingAreTheReturnMinusTheValue() {
    // gamma = lambda = 1: the advantage is the sum of the following rewards minus the value
    var trajectory = List.of(step(1, 0.3f), step(-2, 0.1f), step(4, 0.7f));
    Rollout.estimateAdvantages(trajectory, 1.0, 1.0);
    assertEquals(3 - 0.3, trajectory.get(0).advantage, 1e-6);
    assertEquals(2 - 0.1, trajectory.get(1).advantage, 1e-6);
    assertEquals(4 - 0.7, trajectory.get(2).advantage, 1e-6);
    // the value targets are the returns
    assertEquals(3.0, trajectory.get(0).valueTarget, 1e-6);
    assertEquals(4.0, trajectory.get(2).valueTarget, 1e-6);
  }

  @Test
  public void testPerfectValuesGiveNoAdvantage() {
    // values equal to the discounted returns: nothing was surprising
    double gamma = 0.9;
    var trajectory = List.of(step(0, (float) (gamma * 1.0)), step(1, 1.0f));
    Rollout.estimateAdvantages(trajectory, gamma, 0.95);
    assertEquals(0, trajectory.get(0).advantage, 1e-6);
    assertEquals(0, trajectory.get(1).advantage, 1e-6);
  }
}
