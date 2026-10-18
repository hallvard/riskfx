package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.hal.risk.ai.ObservationEncoder;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

/** In this package, to be able to set up a board. */
public class ObservationEncoderTest {

  private static final double LOG_SCALE = Math.log1p(50);

  private RiskEnv env;
  private ObservationEncoder encoder;
  private Territory alaska, alberta, kamchatka;

  /** The first player occupies all territories with 2 armies, except Alberta, which the second occupies with 5 armies. */
  private void setUp() {
    env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    var risk = env.risk();
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(risk.players().get(0), 2);
    }
    alaska = risk.getTerritory("Alaska");
    alberta = risk.getTerritory("Alberta");
    kamchatka = risk.getTerritory("Kamchatka");
    alberta.setOccupant(risk.players().get(1), 5);
    encoder = new ObservationEncoder(env);
  }

  private float feature(Observation observation, Territory territory, String name) {
    return observation.territories()[env.territoryIndex(territory)][encoder.territoryFeatureNames().indexOf(name)];
  }

  private float global(Observation observation, String name) {
    return observation.globals()[encoder.globalFeatureNames().indexOf(name)];
  }

  private record Observation(float[][] territories, float[] globals) {
    Observation(no.hal.risk.ai.Observation observation) {
      this(observation.territories(), observation.globals());
    }
  }

  private Observation encode(int player) {
    return new Observation(encoder.encode(env.players().get(player)));
  }

  @Test
  public void testShapes() {
    setUp();
    var observation = encode(0);
    assertEquals(42, observation.territories().length);
    assertEquals(encoder.territoryFeatureNames().size(), observation.territories()[0].length);
    assertEquals(encoder.globalFeatureNames().size(), observation.globals().length);
    // the same number of features on any map, since nothing is per continent
    assertEquals(8 + 5, encoder.territoryFeatureNames().size());
    assertEquals(10 + 4, encoder.globalFeatureNames().size());
  }

  @Test
  public void testTerritoryFeaturesAreRelativeToTheViewer() {
    setUp();
    var me = encode(0);
    var other = encode(1);
    assertEquals(1, feature(me, alaska, "mine"));
    assertEquals(0, feature(me, alaska, "enemy"));
    assertEquals(0, feature(me, alberta, "mine"));
    assertEquals(1, feature(me, alberta, "enemy"));
    // seen from the other player
    assertEquals(0, feature(other, alaska, "mine"));
    assertEquals(1, feature(other, alaska, "enemy"));
    assertEquals(1, feature(other, alberta, "mine"));
    // exactly one of mine and enemy, for all territories and viewers
    for (var observation : new Observation[] {me, other}) {
      for (var territory : env.territories()) {
        assertEquals(1, feature(observation, territory, "mine") + feature(observation, territory, "enemy"));
      }
    }
  }

  @Test
  public void testArmiesAndBorders() {
    setUp();
    var me = encode(0);
    assertEquals((float) (Math.log1p(5) / LOG_SCALE), feature(me, alberta, "armies"), 1e-6);
    assertEquals((float) (Math.log1p(2) / LOG_SCALE), feature(me, alaska, "armies"), 1e-6);
    // total armies: 41 * 2 + 5
    assertEquals(5f / 87, feature(me, alberta, "armyShare"), 1e-6);
    // Alaska borders Alberta, Kamchatka does not
    assertEquals(1, feature(me, alaska, "bordersEnemy"));
    assertEquals(0, feature(me, kamchatka, "bordersEnemy"));
    assertEquals(1, feature(me, alaska, "canAttack"));
    assertEquals(0, feature(me, kamchatka, "canAttack"));
    assertEquals((float) (Math.log1p(5) / LOG_SCALE), feature(me, alaska, "adjacentEnemyArmies"), 1e-6);
    assertEquals(0, feature(me, kamchatka, "adjacentEnemyArmies"));
    // Alberta is attacked from the viewpoint of the other player, but it can only attack from where it has two armies
    var other = encode(1);
    assertEquals(1, feature(other, alberta, "canAttack"));
    assertEquals(0, feature(other, alaska, "canAttack"));
    assertEquals(1, feature(other, alberta, "bordersEnemy"));
  }

  @Test
  public void testCanNotAttackFromSingleArmy() {
    setUp();
    alaska.setOccupant(env.players().get(0), 1);
    assertEquals(0, feature(encode(0), alaska, "canAttack"));
    assertEquals(1, feature(encode(0), alaska, "bordersEnemy"));
  }

  @Test
  public void testContinentFeatures() {
    setUp();
    var me = encode(0);
    // North America has 9 of the 42 territories, one of them held by the other player
    assertEquals(9f / 42, feature(me, alaska, "continentSize"), 1e-6);
    assertEquals(12f / 42, feature(me, kamchatka, "continentSize"), 1e-6);
    assertEquals(8f / 9, feature(me, alaska, "continentMine"), 1e-6);
    assertEquals(1f / 9, feature(me, alaska, "continentBestEnemy"), 1e-6);
    assertEquals(1, feature(me, kamchatka, "continentMine"));
    assertEquals(0, feature(me, kamchatka, "continentBestEnemy"));
    assertEquals(1, feature(encode(1), kamchatka, "continentBestEnemy"));
    // Alaska borders Northwest Territory, Alberta and Kamchatka, which is in another continent
    assertEquals(0.5f, feature(me, alaska, "degree"), 1e-6);
    assertEquals(1, feature(me, alaska, "bordersOtherContinent"));
    assertEquals(0, feature(me, alberta, "bordersOtherContinent"));
    assertEquals(0.5f, feature(me, alaska, "continentBonus"));
    assertEquals(0.7f, feature(me, kamchatka, "continentBonus"), 1e-6);
  }

  @Test
  public void testGlobalFeatures() {
    setUp();
    var me = encode(0);
    var other = encode(1);
    assertEquals(1, global(me, "phase:REINFORCING"));
    assertEquals(0, global(me, "phase:ATTACKING"));
    assertEquals(0, global(me, "phase:FORTIFYING"));
    assertTrue(global(me, "armiesToPlace") > 0);
    assertEquals(41f / 42, global(me, "myTerritoryShare"), 1e-6);
    assertEquals(1f / 42, global(other, "myTerritoryShare"), 1e-6);
    assertEquals(1f / 42, global(me, "bestEnemyTerritoryShare"), 1e-6);
    assertEquals(82f / 87, global(me, "myArmyShare"), 1e-6);
    assertEquals(5f / 87, global(me, "bestEnemyArmyShare"), 1e-6);
    assertEquals(1, global(me, "alivePlayers"));
    assertEquals(0, global(me, "turnProgress"));
    // the bonuses of all continents are 24, and the first player holds all but North America (5)
    assertEquals(19f / 24, global(me, "myBonusShare"), 1e-6);
    assertEquals(0, global(me, "bestEnemyBonusShare"));
    assertEquals(0, global(other, "myBonusShare"));
    assertEquals(19f / 24, global(other, "bestEnemyBonusShare"), 1e-6);
    assertEquals(1, global(me, "myBestContinentFraction"));
    assertEquals(1f / 9, global(me, "bestEnemyBestContinentFraction"), 1e-6);
    assertEquals(1f / 9, global(other, "myBestContinentFraction"), 1e-6);
    assertEquals(1, global(other, "bestEnemyBestContinentFraction"));
  }

  @Test
  public void testAdjacency() {
    setUp();
    var adjacency = encoder.adjacency();
    int count = 0;
    for (int i = 0; i < adjacency.length; i++) {
      for (int j = 0; j < adjacency.length; j++) {
        assertEquals(adjacency[i][j], adjacency[j][i]);
        count += adjacency[i][j] ? 1 : 0;
      }
    }
    // 83 borders, in both directions
    assertEquals(166, count);
    assertTrue(adjacency[env.territoryIndex(alaska)][env.territoryIndex(kamchatka)]);
    assertTrue(adjacency[env.territoryIndex(alaska)][env.territoryIndex(alberta)]);
  }

  @Test
  public void testArmiesToPlace() {
    setUp();
    var reinforcing = (TurnState.Reinforcing) env.risk().currentTurnState();
    assertEquals((float) (reinforcing.totalArmies() / 20.0), global(encode(0), "armiesToPlace"), 1e-6);
  }
}
