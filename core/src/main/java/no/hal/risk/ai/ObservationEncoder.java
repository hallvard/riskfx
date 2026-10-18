package no.hal.risk.ai;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.Player;
import no.hal.risk.Territory;
import no.hal.risk.TurnState;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;

/**
 * Translates the state of a game to numbers, as seen by one player.
 * The view is relative to the viewer ("mine" and "enemy"), so a single network can play any seat.
 * The encoding doesn't depend on the identity of the other players, only on whether a territory is the viewer's or not.
 */
public class ObservationEncoder {

  /** Armies are log-scaled, and this is the number of armies that gives 1. */
  private static final double LOG_SCALE = Math.log1p(50);
  /** Largest number of armies to place that we expect, giving 1. */
  private static final double MAX_ARMIES_TO_PLACE = 20;
  private static final double MAX_CONTINENT_BONUS = 10;
  /** The number of neighbours that gives 1. */
  private static final double MAX_DEGREE = 6;

  private final RiskEnv env;
  private final int[] continentOfTerritory;
  private final List<String> territoryFeatureNames = new ArrayList<>();
  private final List<String> globalFeatureNames = new ArrayList<>();

  public ObservationEncoder(RiskEnv env) {
    this.env = env;
    var continents = env.risk().continents();
    continentOfTerritory = new int[env.territories().size()];
    for (int c = 0; c < continents.size(); c++) {
      for (var territory : continents.get(c).territories()) {
        continentOfTerritory[env.territoryIndex(territory)] = c;
      }
    }
    territoryFeatureNames.addAll(List.of("mine", "enemy", "armies", "armyShare", "canAttack", "bordersEnemy",
        "adjacentEnemyArmies", "continentBonus", "continentSize", "continentMine", "continentBestEnemy", "degree", "bordersOtherContinent"));
    globalFeatureNames.addAll(List.of("phase:REINFORCING", "phase:ATTACKING", "phase:FORTIFYING", "armiesToPlace",
        "myTerritoryShare", "myArmyShare", "bestEnemyTerritoryShare", "bestEnemyArmyShare", "alivePlayers", "turnProgress",
        "myBonusShare", "bestEnemyBonusShare", "myBestContinentFraction", "bestEnemyBestContinentFraction"));
  }

  public List<String> territoryFeatureNames() {
    return territoryFeatureNames;
  }

  public List<String> globalFeatureNames() {
    return globalFeatureNames;
  }

  /** Which territories are adjacent, in the order of RiskEnv.territories(). Needed for message passing. */
  public boolean[][] adjacency() {
    int n = env.territories().size();
    var adjacency = new boolean[n][n];
    for (int i = 0; i < n; i++) {
      for (var neighbour : env.risk().world().neighbours(env.territories().get(i))) {
        adjacency[i][env.territoryIndex(neighbour)] = true;
      }
    }
    return adjacency;
  }

  private static float scaledArmies(double armies) {
    return (float) (Math.log1p(armies) / LOG_SCALE);
  }

  public Observation encode(Player viewer) {
    var risk = env.risk();
    var territories = env.territories();
    int n = territories.size();
    int playerCount = env.players().size();
    int continentCount = risk.continents().size();

    // totals
    int[] territoryCounts = new int[playerCount];
    double[] armyCounts = new double[playerCount];
    double[][] continentCounts = new double[playerCount][continentCount];
    double totalArmies = 0;
    for (int i = 0; i < n; i++) {
      var territory = territories.get(i);
      int player = env.playerIndex(territory.occupant());
      territoryCounts[player]++;
      armyCounts[player] += territory.numArmies();
      continentCounts[player][continentOfTerritory[i]]++;
      totalArmies += territory.numArmies();
    }

    // how much of each continent the viewer and the strongest of the others hold, and what the continents give if held
    int me = env.playerIndex(viewer);
    double[] bestEnemyCount = new double[continentCount];
    for (int player = 0; player < playerCount; player++) {
      if (player != me) {
        for (int c = 0; c < continentCount; c++) {
          bestEnemyCount[c] = Math.max(bestEnemyCount[c], continentCounts[player][c]);
        }
      }
    }
    double totalBonus = 0, myBonus = 0, bestEnemyBonus = 0, myBestFraction = 0, bestEnemyBestFraction = 0;
    for (int c = 0; c < continentCount; c++) {
      var continent = risk.continents().get(c);
      int size = continent.territories().size();
      totalBonus += continent.bonusPoints();
      myBonus += continentCounts[me][c] == size ? continent.bonusPoints() : 0;
      bestEnemyBonus += bestEnemyCount[c] == size ? continent.bonusPoints() : 0;
      myBestFraction = Math.max(myBestFraction, continentCounts[me][c] / size);
      bestEnemyBestFraction = Math.max(bestEnemyBestFraction, bestEnemyCount[c] / size);
    }

    var territoryFeatures = new float[n][territoryFeatureNames.size()];
    for (int i = 0; i < n; i++) {
      var territory = territories.get(i);
      var row = territoryFeatures[i];
      boolean mine = territory.occupant() == viewer;
      double adjacentEnemyArmies = 0;
      for (var neighbour : risk.world().neighbours(territory)) {
        if (neighbour.occupant() != territory.occupant()) {
          adjacentEnemyArmies += neighbour.numArmies();
        }
      }
      int f = 0;
      row[f++] = mine ? 1 : 0;
      row[f++] = mine ? 0 : 1;
      row[f++] = scaledArmies(territory.numArmies());
      row[f++] = (float) (territory.numArmies() / totalArmies);
      row[f++] = mine && territory.numArmies() >= 2 && adjacentEnemyArmies > 0 ? 1 : 0;
      row[f++] = adjacentEnemyArmies > 0 ? 1 : 0;
      row[f++] = scaledArmies(adjacentEnemyArmies);
      var continent = risk.continents().get(continentOfTerritory[i]);
      row[f++] = (float) (continent.bonusPoints() / MAX_CONTINENT_BONUS);
      int c = continentOfTerritory[i];
      row[f++] = (float) continent.territories().size() / n;
      row[f++] = (float) (continentCounts[me][c] / continent.territories().size());
      row[f++] = (float) (bestEnemyCount[c] / continent.territories().size());
      var neighbours = risk.world().neighbours(territory);
      row[f++] = (float) (neighbours.size() / MAX_DEGREE);
      row[f++] = neighbours.stream().anyMatch(neighbour -> continentOfTerritory[env.territoryIndex(neighbour)] != c) ? 1 : 0;
    }

    // the viewer compared to the best of the others
    double bestEnemyTerritories = 0, bestEnemyArmies = 0;
    int alive = 0;
    for (int player = 0; player < playerCount; player++) {
      if (territoryCounts[player] > 0) {
        alive++;
      }
      if (player != me) {
        bestEnemyTerritories = Math.max(bestEnemyTerritories, territoryCounts[player]);
        bestEnemyArmies = Math.max(bestEnemyArmies, armyCounts[player]);
      }
    }
    var globals = new float[globalFeatureNames.size()];
    int g = 0;
    var phase = env.phase();
    globals[g++] = phase == Phase.REINFORCING ? 1 : 0;
    globals[g++] = phase == Phase.ATTACKING ? 1 : 0;
    globals[g++] = phase == Phase.FORTIFYING ? 1 : 0;
    globals[g++] = risk.currentTurnState() instanceof TurnState.Reinforcing reinforcing
        ? (float) (reinforcing.totalArmies() / MAX_ARMIES_TO_PLACE) : 0;
    globals[g++] = (float) territoryCounts[me] / n;
    globals[g++] = (float) (armyCounts[me] / totalArmies);
    globals[g++] = (float) (bestEnemyTerritories / n);
    globals[g++] = (float) (bestEnemyArmies / totalArmies);
    globals[g++] = (float) alive / playerCount;
    globals[g++] = (float) env.turns() / env.maxTurns();
    globals[g++] = (float) (myBonus / totalBonus);
    globals[g++] = (float) (bestEnemyBonus / totalBonus);
    globals[g++] = (float) myBestFraction;
    globals[g++] = (float) bestEnemyBestFraction;
    return new Observation(territoryFeatures, globals);
  }
}
