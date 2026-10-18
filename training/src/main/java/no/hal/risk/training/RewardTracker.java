package no.hal.risk.training;

import no.hal.risk.ai.*;

import no.hal.risk.env.RiskEnv;

/**
 * Computes the reward of each decision in a game. The reward of a decision is measured from the state where the
 * player decided to the state where it decides next, so the moves of the other players in between are included.
 * (Measuring right after its own action would leave them out, and the shaping would no longer cancel out over the game.)
 *
 * <pre>
 * tracker.reset();
 * while (! env.isDone()) {
 *   double previousReward = tracker.onDecision(env); // for the previous decision of the current player
 *   ... choose and perform an action ...
 * }
 * double[] lastRewards = tracker.onGameEnd(env);     // for the last decision of each player
 * </pre>
 */
public class RewardTracker {

  private final Reward reward;
  private final double[] lastPotentials;
  private final boolean[] hasDecided;

  public RewardTracker(Reward reward, int playerCount) {
    this.reward = reward;
    this.lastPotentials = new double[playerCount];
    this.hasDecided = new boolean[playerCount];
  }

  public void reset() {
    java.util.Arrays.fill(hasDecided, false);
  }

  /** Call before the current player chooses an action. Returns the reward of its previous decision, or 0 for the first. */
  public double onDecision(RiskEnv env) {
    int player = env.playerIndex(env.currentPlayer());
    double potential = reward.evaluation().potential(env, player);
    double previousReward = hasDecided[player] ? reward.shaping(lastPotentials[player], potential) : 0;
    lastPotentials[player] = potential;
    hasDecided[player] = true;
    return previousReward;
  }

  /** Call when the game is over. Returns the reward of the last decision of each player, including the outcome. */
  public double[] onGameEnd(RiskEnv env) {
    var rewards = new double[lastPotentials.length];
    for (int player = 0; player < rewards.length; player++) {
      if (hasDecided[player]) {
        rewards[player] = reward.shaping(lastPotentials[player], 0) + reward.terminal(env, player);
      }
    }
    return rewards;
  }
}
