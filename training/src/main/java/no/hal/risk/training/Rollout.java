package no.hal.risk.training;

import no.hal.risk.ai.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The decisions of the network recorded during self-play, with what is needed to improve the policy.
 * Each trajectory is the decisions of one player in one game, in order.
 */
public class Rollout {

  /** One decision. The reward is filled in when the next decision of the player, or the end of the game, is reached. */
  public static class Step {
    public final Observation observation;
    /** The positions of the legal actions in the action space. */
    public final int[] legal;
    public final int action;
    /** The log of the probability the policy gave the action when it was chosen. */
    public final float logProb;
    /** The value the network estimated for the state, when the action was chosen. */
    public final float value;
    public float reward;
    public float advantage;
    public float valueTarget;

    public Step(Observation observation, int[] legal, int action, float logProb, float value) {
      this.observation = observation;
      this.legal = legal;
      this.action = action;
      this.logProb = logProb;
      this.value = value;
    }
  }

  private final List<Step> steps = new ArrayList<>();
  private int games;
  private int truncatedGames;
  private long totalTurns;
  private int gamesWithBots;
  private int gamesWithBotsWonByNetwork;
  private long networkEliminations;

  public List<Step> steps() {
    return steps;
  }

  public int size() {
    return steps.size();
  }

  public int games() {
    return games;
  }

  public int truncatedGames() {
    return truncatedGames;
  }

  public double averageTurns() {
    return games == 0 ? 0 : (double) totalTurns / games;
  }

  /** The share of games against bots won by the network, NaN if there were none. */
  public double winRateAgainstBots() {
    return gamesWithBots == 0 ? Double.NaN : (double) gamesWithBotsWonByNetwork / gamesWithBots;
  }

  /** The average number of opponents the network eliminated in a game, by its own attacks. */
  public double eliminationsPerGame() {
    return games == 0 ? 0 : (double) networkEliminations / games;
  }

  void addGame(int turns, boolean truncated, boolean hadBots, boolean networkWon, int eliminations) {
    games++;
    networkEliminations += eliminations;
    totalTurns += turns;
    truncatedGames += truncated ? 1 : 0;
    if (hadBots) {
      gamesWithBots++;
      gamesWithBotsWonByNetwork += networkWon ? 1 : 0;
    }
  }

  /**
   * Generalized advantage estimation for the trajectory of a player, whose last step ends the game.
   * The advantage of a step is how much better its reward plus the value of what follows turned out
   * than the value the network expected, smoothed over the following steps with {@code lambda}.
   * The value target for the network is the advantage plus its own estimate.
   */
  public static void estimateAdvantages(List<Step> trajectory, double gamma, double lambda) {
    double advantage = 0;
    for (int k = trajectory.size() - 1; k >= 0; k--) {
      var step = trajectory.get(k);
      double nextValue = k + 1 < trajectory.size() ? trajectory.get(k + 1).value : 0;
      double delta = step.reward + gamma * nextValue - step.value;
      advantage = delta + gamma * lambda * advantage;
      step.advantage = (float) advantage;
      step.valueTarget = (float) (advantage + step.value);
    }
  }

  void addTrajectory(List<Step> trajectory) {
    steps.addAll(trajectory);
  }
}
