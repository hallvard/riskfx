package no.hal.risk.training;

import no.hal.risk.ai.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class RewardTest {

  /**
   * Plays a game with greedy bots and, for each player, collects the shaping reward of each of its decisions,
   * in the order of the decisions, with the first potential of the player.
   */
  private record Game(RiskEnv env, List<List<Double>> shapings, double[] firstPotentials, double[] lastRewards) {
  }

  private Game play(RiskConfig config, int maxTurns, long seed, Reward reward) {
    var env = new RiskEnv(config, maxTurns);
    env.reset(seed);
    int players = config.playerCount();
    var tracker = new RewardTracker(reward, players);
    tracker.reset();
    var shapings = new ArrayList<List<Double>>();
    var decisions = new int[players];
    var firstPotentials = new double[players];
    for (int p = 0; p < players; p++) {
      shapings.add(new ArrayList<>());
    }
    var bot = new GreedyBot();
    while (! env.isDone()) {
      int player = env.playerIndex(env.currentPlayer());
      if (decisions[player]++ == 0) {
        firstPotentials[player] = reward.evaluation().potential(env, player);
      }
      double previous = tracker.onDecision(env);
      if (decisions[player] > 1) {
        shapings.get(player).add(previous);
      }
      env.step(bot.chooseAction(env, env.legalActions()));
    }
    return new Game(env, shapings, firstPotentials, tracker.onGameEnd(env));
  }

  @Test
  public void testShapingCancelsOutOverAGame() {
    // the discounted sum of the shaping is minus the first potential, since the last potential is 0
    for (double gamma : new double[] {1.0, 0.99, 0.9}) {
      var reward = new Reward(new Reward.Config(gamma, 1, -1, -0.25), new BoardEvaluation());
      for (var config : List.of(new RiskConfig(2), new RiskConfig(4))) {
        var game = play(config, 400, 17, reward);
        for (int player = 0; player < config.playerCount(); player++) {
          var rewards = new ArrayList<>(game.shapings().get(player));
          rewards.add(game.lastRewards()[player] - reward.terminal(game.env(), player));
          double discounted = 0;
          double factor = 1;
          for (double r : rewards) {
            discounted += factor * r;
            factor *= gamma;
          }
          assertEquals(-game.firstPotentials()[player], discounted, 1e-9, "player " + player + " gamma " + gamma);
        }
      }
    }
  }

  @Test
  public void testShapingStillCancelsOutWhenPlayersAreEliminated() {
    // a game of four players, where players are eliminated on the way
    for (long seed : new long[] {17, 18, 19}) {
      var reward = Reward.named("elimination");
      var game = play(new RiskConfig(4), 400, seed, reward);
      assertTrue(game.env().risk().alivePlayers().size() < 4, "somebody should have been eliminated");
      double gamma = reward.config().gamma();
      for (int player = 0; player < 4; player++) {
        var rewards = new ArrayList<>(game.shapings().get(player));
        rewards.add(game.lastRewards()[player] - reward.terminal(game.env(), player));
        double discounted = 0, factor = 1;
        for (double r : rewards) {
          discounted += factor * r;
          factor *= gamma;
        }
        assertEquals(-game.firstPotentials()[player], discounted, 1e-9, "player " + player + ", seed " + seed);
      }
    }
  }

  @Test
  public void testWinnerAndLosersGetTheTerminalReward() {
    // without discounting, the total return is the terminal reward minus the first potential
    var reward = new Reward(new Reward.Config(1.0, 1, -1, -0.25), new BoardEvaluation());
    var game = play(new RiskConfig(2), 400, 2, reward);
    assertNotNull(game.env().winner(), "this seed should give a winner");
    int winner = game.env().playerIndex(game.env().winner());
    assertEquals(1, reward.terminal(game.env(), winner));
    assertEquals(-1, reward.terminal(game.env(), 1 - winner));
    for (int player = 0; player < 2; player++) {
      double total = game.shapings().get(player).stream().mapToDouble(Double::doubleValue).sum() + game.lastRewards()[player];
      assertEquals(reward.terminal(game.env(), player) - game.firstPotentials()[player], total, 1e-9);
    }
  }

  @Test
  public void testTruncatedGameIsADraw() {
    var reward = new Reward(new Reward.Config(0.99, 1, -1, -0.25), new BoardEvaluation());
    var game = play(new RiskConfig(2), 3, 2, reward);
    assertTrue(game.env().isTruncated());
    assertEquals(-0.25, reward.terminal(game.env(), 0));
    assertEquals(-0.25, reward.terminal(game.env(), 1));
  }

  @Test
  public void testNoTerminalRewardBeforeTheEnd() {
    var reward = new Reward();
    var env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    assertFalse(env.isDone());
    assertEquals(0, reward.terminal(env, 0));
    assertEquals(0, reward.terminal(env, 1));
  }

  @Test
  public void testFirstDecisionHasNoPreviousReward() {
    var env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    var tracker = new RewardTracker(new Reward(), 2);
    tracker.reset();
    assertEquals(0, tracker.onDecision(env));
  }

  @Test
  public void testAucOfPerfectReversedAndUninformativeScores() {
    assertEquals(1.0, PotentialDiagnostics.auc(new double[] {1, 2, 3, 4}, new boolean[] {false, false, true, true}));
    assertEquals(0.0, PotentialDiagnostics.auc(new double[] {1, 2, 3, 4}, new boolean[] {true, true, false, false}));
    assertEquals(0.5, PotentialDiagnostics.auc(new double[] {5, 5, 5, 5}, new boolean[] {true, false, true, false}));
    assertEquals(0.75, PotentialDiagnostics.auc(new double[] {1, 2, 3, 4}, new boolean[] {false, true, false, true}));
    assertTrue(Double.isNaN(PotentialDiagnostics.auc(new double[] {1, 2}, new boolean[] {true, true})));
  }
}
