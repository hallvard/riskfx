package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class ActionSpaceTest {

  @Test
  public void testSize() {
    // 42 reinforce, 42*42 attack, end attack, 42*42 fortify, skip fortify
    assertEquals(42 + 1764 + 1 + 1764 + 1, new ActionSpace(42).size());
  }

  @Test
  public void testIndicesAreDistinct() {
    var space = new ActionSpace(42);
    var indices = new HashSet<Integer>();
    for (int i = 0; i < 42; i++) {
      assertTrue(indices.add(space.index(new Action.Reinforce(i))));
      for (int j = 0; j < 42; j++) {
        assertTrue(indices.add(space.index(new Action.Attack(i, j))));
        assertTrue(indices.add(space.index(new Action.Fortify(i, j, 1))));
      }
    }
    assertTrue(indices.add(space.index(new Action.EndAttack())));
    assertTrue(indices.add(space.index(new Action.SkipFortify())));
    assertEquals(space.size(), indices.size());
    assertTrue(indices.stream().allMatch(index -> index >= 0 && index < space.size()));
  }

  @Test
  public void testEveryPositionIsAnActionWithThatPosition() {
    var env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    var space = new ActionSpace(env.territories().size());
    for (int index = 0; index < space.size(); index++) {
      assertEquals(index, space.index(space.action(index, env)));
    }
    assertThrows(IllegalArgumentException.class, () -> space.action(-1, env));
    assertThrows(IllegalArgumentException.class, () -> space.action(space.size(), env));
  }

  @Test
  public void testMaskIsTheLegalActionsAtEveryStepOfAGame() {
    var env = new RiskEnv(new RiskConfig(3), 100);
    env.reset(3);
    var space = new ActionSpace(env.territories().size());
    var random = new java.util.Random(9);
    int steps = 0;
    while (! env.isDone()) {
      var legal = env.legalActions();
      var mask = space.mask(legal);
      assertEquals(legal.size(), count(mask), "in phase " + env.phase());
      for (var action : legal) {
        assertTrue(mask[space.index(action)]);
        // converting back gives the same action, also for fortification which moves all movable armies
        assertEquals(action, space.action(space.index(action), env));
      }
      env.step(legal.get(random.nextInt(legal.size())));
      steps++;
    }
    assertTrue(steps > 50);
  }

  @Test
  public void testMaskedActionsAreTheIllegalOnes() {
    var env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(4);
    var space = new ActionSpace(env.territories().size());
    var mask = space.mask(env.legalActions());
    // at the start only reinforcement is possible: nothing else may be selected
    for (int index = 0; index < space.size(); index++) {
      assertEquals(mask[index], space.action(index, env) instanceof Action.Reinforce reinforce
          && env.territories().get(reinforce.territory()).occupant() == env.currentPlayer());
    }
  }

  private static int count(boolean[] mask) {
    int count = 0;
    for (boolean value : mask) {
      count += value ? 1 : 0;
    }
    return count;
  }
}
