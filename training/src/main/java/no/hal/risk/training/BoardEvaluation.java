package no.hal.risk.training;

import no.hal.risk.ai.*;

import no.hal.risk.Territory;
import no.hal.risk.env.RiskEnv;

/**
 * A hand-made evaluation of how good a board is for a player, used as the potential for reward shaping.
 * The score of a player is a weighted sum of
 * <ul>
 * <li>its share of the territories</li>
 * <li>its share of the armies</li>
 * <li>the share of all continent bonuses it gets</li>
 * <li>minus its exposure: the armies of adjacent enemies exceeding the armies of its territory, as a share of all armies</li>
 * <li>its attack advantage: for each territory, the armies it can send in beyond its weakest adjacent enemy,
 *   {@code max(0, armies - 1 - weakest enemy neighbour)}, either summed up (plain or capped per territory) or as the share of territories having any</li>
 * </ul>
 * The potential of a player is its score compared to the average of the others, plus the share of its opponents that are
 * eliminated (when there are more than two players), weighted, all scaled, and 0 when the game is over.
 * The elimination term makes it worth finishing off a weak opponent, not only taking its territories.
 */
public class BoardEvaluation {

  /** The most a single territory contributes to the capped attack advantage, in armies. */
  public static final int ADVANTAGE_CAP = 6;

  /**
   * @param advantage weight of the attack advantage, summed over all territories and normalized by all armies
   * @param cappedAdvantage weight of the attack advantage with at most {@link #ADVANTAGE_CAP} for each territory, normalized by the maximum
   * @param advantageCount weight of the share of territories that can attack with an advantage
   * @param eliminations weight of the share of the opponents that are eliminated, which is 1 when the player is the only one left
   */
  public record Weights(double territories, double armies, double continents, double exposure,
      double advantage, double cappedAdvantage, double advantageCount, double eliminations, double scale) {

    /** Without a reward for eliminating opponents. */
    public Weights(double territories, double armies, double continents, double exposure,
        double advantage, double cappedAdvantage, double advantageCount, double scale) {
      this(territories, armies, continents, exposure, advantage, cappedAdvantage, advantageCount, 0, scale);
    }

    /**
     * Exposure has weight 0: it didn't predict the winner in greedy games, see {@link PotentialDiagnostics}.
     */
    public static Weights defaults() {
      return new Weights(1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.3);
    }

    /** The defaults, and a reward for eliminating opponents. */
    public static Weights withEliminations() {
      return new Weights(1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.3);
    }

    public static Weights only(double territories, double armies, double continents, double exposure) {
      return new Weights(territories, armies, continents, exposure, 0, 0, 0, 0, 1.0);
    }

    public static Weights only(double territories, double armies, double continents, double advantage, double cappedAdvantage, double advantageCount) {
      return new Weights(territories, armies, continents, 0, advantage, cappedAdvantage, advantageCount, 0, 1.0);
    }
  }

  private final Weights weights;

  public BoardEvaluation(Weights weights) {
    this.weights = weights;
  }

  public BoardEvaluation() {
    this(Weights.defaults());
  }

  /** The score of each player, in the order of RiskEnv.players(). */
  public double[] scores(RiskEnv env) {
    var risk = env.risk();
    int playerCount = env.players().size();
    int[] territoryCounts = new int[playerCount];
    double[] armyCounts = new double[playerCount];
    double[] exposures = new double[playerCount];
    double[] continentBonuses = new double[playerCount];
    double[] advantages = new double[playerCount];
    double[] cappedAdvantages = new double[playerCount];
    int[] advantageCounts = new int[playerCount];
    double totalArmies = 0;
    for (Territory territory : env.territories()) {
      int player = env.playerIndex(territory.occupant());
      territoryCounts[player]++;
      armyCounts[player] += territory.numArmies();
      totalArmies += territory.numArmies();
      int adjacentEnemyArmies = 0;
      int weakestEnemy = Integer.MAX_VALUE;
      for (var neighbour : risk.world().neighbours(territory)) {
        if (neighbour.occupant() != territory.occupant()) {
          adjacentEnemyArmies += neighbour.numArmies();
          weakestEnemy = Math.min(weakestEnemy, neighbour.numArmies());
        }
      }
      exposures[player] += Math.max(0, adjacentEnemyArmies - territory.numArmies());
      if (weakestEnemy != Integer.MAX_VALUE) {
        int lead = Math.max(0, territory.numArmies() - 1 - weakestEnemy);
        advantages[player] += lead;
        cappedAdvantages[player] += Math.min(ADVANTAGE_CAP, lead);
        advantageCounts[player] += lead > 0 ? 1 : 0;
      }
    }
    double totalBonus = 0;
    for (var continent : risk.continents()) {
      totalBonus += continent.bonusPoints();
      var occupant = risk.continentOccupant(continent);
      if (occupant != null) {
        continentBonuses[env.playerIndex(occupant)] += continent.bonusPoints();
      }
    }
    double[] scores = new double[playerCount];
    for (int player = 0; player < playerCount; player++) {
      scores[player] = weights.territories() * territoryCounts[player] / env.territories().size()
          + weights.armies() * armyCounts[player] / totalArmies
          + weights.continents() * continentBonuses[player] / totalBonus
          - weights.exposure() * exposures[player] / totalArmies
          + weights.advantage() * advantages[player] / totalArmies
          + weights.cappedAdvantage() * cappedAdvantages[player] / (ADVANTAGE_CAP * env.territories().size())
          + weights.advantageCount() * advantageCounts[player] / env.territories().size();
    }
    return scores;
  }

  /**
   * The potential of a state for a player: its score compared to the average of the others, and the share of its opponents
   * that are eliminated, weighted. It is 0 when the game is over, as required for potential-based shaping.
   * A player that is eliminated has no more decisions, so the elimination term is only for those still alive.
   */
  public double potential(RiskEnv env, int playerIndex) {
    if (env.isDone()) {
      return 0;
    }
    var scores = scores(env);
    double others = 0;
    for (int player = 0; player < scores.length; player++) {
      if (player != playerIndex) {
        others += scores[player];
      }
    }
    double relative = scores[playerIndex] - others / (scores.length - 1);
    double eliminated = 0;
    if (weights.eliminations() != 0 && env.risk().isAlive(env.players().get(playerIndex))) {
      eliminated = (double) (scores.length - env.risk().alivePlayers().size()) / (scores.length - 1);
    }
    return weights.scale() * (relative + weights.eliminations() * eliminated);
  }
}
