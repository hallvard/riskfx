package no.hal.risk.training;

import no.hal.risk.ai.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class ImitationTest {

  private final RiskConfig config = new RiskConfig(2);

  @Test
  public void testCollectedSamplesAreConsistent() {
    var samples = Imitation.collect(config, 2, 5, new Reward());
    assertTrue(samples.size() > 200);
    for (var sample : samples) {
      assertTrue(java.util.Arrays.stream(sample.legal()).anyMatch(index -> index == sample.action()));
      assertTrue(java.util.Arrays.stream(sample.acceptable()).anyMatch(index -> index == sample.action()), "the chosen action is acceptable");
      assertTrue(Float.isFinite(sample.valueTarget()));
    }
  }

  @Test
  public void testCollectionIsRepeatable() {
    var first = Imitation.collect(config, 1, 7, new Reward());
    var second = Imitation.collect(config, 1, 7, new Reward());
    assertEquals(first.size(), second.size());
    for (int i = 0; i < first.size(); i++) {
      assertEquals(first.get(i).action(), second.get(i).action());
      assertEquals(first.get(i).valueTarget(), second.get(i).valueTarget());
    }
  }

  @Test
  public void testValueTargetsAreDiscountedRewards() {
    // without discounting, the target of the first decision of a player is its terminal reward minus the first potential
    var noDiscount = new Reward(new Reward.Config(1.0, 1, -1, -0.25), new BoardEvaluation());
    var env = new RiskEnv(config);
    env.reset(11);
    var samples = Imitation.collect(config, 1, 11, noDiscount);
    // the games are played by two players taking turns, so the first sample is of the first player, and the second player decides later
    var first = samples.stream().filter(sample -> sample.kind() == Imitation.Kind.REINFORCE).findFirst().orElseThrow();
    double potential = noDiscount.evaluation().potential(env, 0);
    var finished = new RiskEnv(config);
    finished.reset(11);
    var bot = new GreedyBot();
    while (! finished.isDone()) {
      finished.step(bot.chooseAction(finished, finished.legalActions()));
    }
    double terminal = noDiscount.terminal(finished, 0);
    assertEquals(terminal - potential, first.valueTarget(), 1e-4);
  }
}
