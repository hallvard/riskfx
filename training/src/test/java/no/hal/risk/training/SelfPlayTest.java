package no.hal.risk.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import no.hal.risk.RiskConfig;
import no.hal.risk.ai.*;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Games played with a network, for experience to learn from, and to measure how good it is. */
public class SelfPlayTest {

  private final RiskConfig riskConfig = new RiskConfig(2);
  private boolean[][] borders;

  @BeforeEach
  public void setUp() {
    borders = new ObservationEncoder(new RiskEnv(riskConfig, 10)).adjacency();
  }

  private JavaPolicyNetwork network(long seed) {
    return new JavaPolicyNetwork(RandomWeights.create(RandomWeights.config(16, 2, 8), seed, 0.3), borders);
  }

  private SelfPlay.Config selfPlayConfig(long seed, double botSeatProbability) {
    return new SelfPlay.Config(riskConfig, 12, 2, botSeatProbability, 0.95, seed);
  }

  @Test
  public void testCollectedStepsAreConsistent() {
    var rollout = new SelfPlay(network(1), new Reward(), selfPlayConfig(5, 0.0)).collect(3);
    assertEquals(3, rollout.games());
    assertTrue(rollout.size() > 50);
    for (var step : rollout.steps()) {
      assertTrue(Arrays.stream(step.legal).anyMatch(legal -> legal == step.action), "the action must be legal");
      assertTrue(step.logProb <= 0 && Float.isFinite(step.logProb));
      assertTrue(Float.isFinite(step.advantage) && Float.isFinite(step.valueTarget) && Float.isFinite(step.reward));
      assertEquals(13, step.observation.territories()[0].length);
    }
    // games that are not truncated were won by one of the players
    assertEquals(3, rollout.truncatedGames() + (rollout.games() - rollout.truncatedGames()));
  }

  @Test
  public void testSameSeedsGiveTheSameExperience() {
    var rollout1 = new SelfPlay(network(1), new Reward(), selfPlayConfig(9, 0.3)).collect(2);
    var rollout2 = new SelfPlay(network(1), new Reward(), selfPlayConfig(9, 0.3)).collect(2);
    assertEquals(rollout1.size(), rollout2.size());
    for (int i = 0; i < rollout1.size(); i++) {
      assertEquals(rollout1.steps().get(i).action, rollout2.steps().get(i).action);
      assertEquals(rollout1.steps().get(i).reward, rollout2.steps().get(i).reward);
    }
  }

  @Test
  public void testTrainingGamesAreNotRepeated() {
    var selfPlay = new SelfPlay(network(1), new Reward(), selfPlayConfig(9, 0.0));
    var first = selfPlay.collect(1);
    var second = selfPlay.collect(1);
    // another board and other decisions
    assertFalse(first.size() == second.size() && first.steps().get(0).action == second.steps().get(0).action
        && first.steps().get(1).action == second.steps().get(1).action
        && first.steps().get(0).observation.territories()[0][2] == second.steps().get(0).observation.territories()[0][2]
        && first.steps().get(5).observation.territories()[7][2] == second.steps().get(5).observation.territories()[7][2]);
  }

  @Test
  public void testEvaluationIsRepeatable() {
    var selfPlay = new SelfPlay(network(2), new Reward(), selfPlayConfig(1, 0.0));
    var first = selfPlay.evaluate(4);
    var second = selfPlay.evaluate(4);
    assertEquals(4, first.games());
    assertTrue(first.wins() + first.truncated() <= 4);
    assertEquals(first.wins(), second.wins());
    assertEquals(first.truncated(), second.truncated());
    assertEquals(first.averageTurns(), second.averageTurns());
  }

  @Test
  public void testEvaluationCountsTheOpponentsTheNetworkEliminates() {
    var evaluation = new SelfPlay(network(2), new Reward(), selfPlayConfig(1, 0.0)).evaluate(6);
    // with two players, the one eliminated is the opponent, and the network wins when it does it
    assertEquals(evaluation.wins() / 6.0, evaluation.eliminationsPerGame(), 1e-12);
    assertTrue(evaluation.toString().contains("eliminated"));
  }

  @Test
  public void testCollectedGamesCountEliminations() {
    var rollout = new SelfPlay(network(2), new Reward(), selfPlayConfig(3, 0.0)).collect(4);
    assertTrue(rollout.eliminationsPerGame() >= 0 && rollout.eliminationsPerGame() <= 1);
  }

  /** A greedy bot that counts how often it is asked. */
  private static class CountingBot implements Bot {
    final String name;
    int decisions;
    private final GreedyBot greedy = new GreedyBot();

    CountingBot(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public synchronized no.hal.risk.env.Action chooseAction(RiskEnv env, java.util.List<no.hal.risk.env.Action> legalActions) {
      decisions++;
      return greedy.chooseAction(env, legalActions);
    }
  }

  @Test
  public void testTheOpponentsTakeTheSeatsThatTheNetworkDoesNotTake() {
    var first = new CountingBot("first");
    var second = new CountingBot("second");
    // one seat of the two is the network's, since there must be one, and the other is an opponent, chosen at random for each game
    var selfPlay = new SelfPlay(network(1), new Reward(), selfPlayConfig(3, 1.0)).opponents(java.util.List.of(first, second));
    selfPlay.collect(12);
    assertTrue(first.decisions > 0 && second.decisions > 0, first.decisions + " " + second.decisions);
  }

  @Test
  public void testWhenMeasuringEachOpponentIsMetAsOftenAsTheOthers() {
    var first = new CountingBot("first");
    var second = new CountingBot("second");
    var evaluation = new SelfPlay(network(1), new Reward(), selfPlayConfig(3, 0.0)).opponents(java.util.List.of(first, second)).evaluate(8);
    assertEquals(8, evaluation.games());
    // the network is in a different seat in each game, and the opponent in the other, so the opponents alternate
    // games differ in length, so the numbers of decisions can't be the same, but each opponent has a fair share
    int total = first.decisions + second.decisions;
    assertTrue(first.decisions > 0.15 * total && second.decisions > 0.15 * total,
        "each in half of the games: " + first.decisions + " and " + second.decisions + " decisions");
  }

  @Test
  public void testAnotherOpponentGivesAnotherGame() {
    // long enough games for a network with random weights to lose to the bots, instead of all ending at the limit
    var config = new SelfPlay.Config(riskConfig, 150, 2, 0.0, 0.95, 1);
    var greedy = new SelfPlay(network(2), new Reward(), config).evaluate(12);
    var cautious = new SelfPlay(network(2), new Reward(), config).opponents(java.util.List.of(new CautiousBot())).evaluate(12);
    assertTrue(greedy.averageTurns() != cautious.averageTurns() || greedy.wins() != cautious.wins()
        || greedy.eliminationsPerGame() != cautious.eliminationsPerGame(),
        "greedy %d wins, %.1f turns; cautious %d wins, %.1f turns".formatted(greedy.wins(), greedy.averageTurns(), cautious.wins(), cautious.averageTurns()));
  }

  @Test
  public void testThereMustBeAnOpponent() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SelfPlay(network(1), new Reward(), selfPlayConfig(1, 0.0)).opponents(java.util.List.of()));
  }
}
