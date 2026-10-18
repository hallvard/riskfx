package no.hal.risk.training;

import no.hal.risk.ai.*;

import no.hal.risk.env.RiskEnv;

/**
 * The reward of a player consists of
 * <ul>
 * <li>a terminal reward when the game is over: won, lost or draw (turn limit). This is the real objective.</li>
 * <li>potential-based shaping, {@code gamma * potential(next) - potential(this)}, which makes learning faster
 *   without changing what the best play is, as long as the potential of a finished game is 0</li>
 * </ul>
 */
public class Reward {

  public record Config(double gamma, double winReward, double loseReward, double drawReward) {

    public static Config defaults() {
      return new Config(0.99, 1.0, -1.0, -0.25);
    }
  }

  private final Config config;
  private final BoardEvaluation evaluation;

  public Reward(Config config, BoardEvaluation evaluation) {
    this.config = config;
    this.evaluation = evaluation;
  }

  public Reward() {
    this(Config.defaults(), new BoardEvaluation());
  }

  /**
   * A reward by name: "default" (territories, armies and continents), "advantage" (also the attack advantage)
   * or "elimination" (as the default, and also eliminating opponents).
   */
  public static Reward named(String name) {
    return switch (name) {
      case "default" -> new Reward();
      case "advantage" -> new Reward(Config.defaults(), new BoardEvaluation(new BoardEvaluation.Weights(1, 1, 1, 0, 1, 0, 0, 0.3)));
      case "elimination" -> new Reward(Config.defaults(), new BoardEvaluation(BoardEvaluation.Weights.withEliminations()));
      default -> throw new IllegalArgumentException("Unknown reward: " + name);
    };
  }

  public Config config() {
    return config;
  }

  public BoardEvaluation evaluation() {
    return evaluation;
  }

  /** The reward for the outcome of the game, 0 as long as it isn't over. */
  public double terminal(RiskEnv env, int playerIndex) {
    if (! env.isDone()) {
      return 0;
    }
    if (env.winner() == null) {
      return config.drawReward();
    }
    return env.playerIndex(env.winner()) == playerIndex ? config.winReward() : config.loseReward();
  }

  public double shaping(double potentialBefore, double potentialAfter) {
    return config.gamma() * potentialAfter - potentialBefore;
  }
}
