package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MapsTest {

  static List<GameMap> maps() {
    return GameMap.all();
  }

  @ParameterizedTest
  @MethodSource("maps")
  public void everyMapIsConnectedSymmetricAndPartitionedIntoContinents(GameMap map) {
    var risk = new RiskConfig(2).withMap(map).createRisk();
    var territories = risk.allTerritories();
    assertEquals(territories.size(), new HashSet<>(territories.stream().map(Territory::name).toList()).size(), "unique names");
    // each territory is in exactly one continent
    var inContinents = risk.continents().stream().flatMap(continent -> continent.territories().stream()).toList();
    assertEquals(territories.size(), inContinents.size());
    for (var continent : risk.continents()) {
      assertTrue(continent.bonusPoints() >= 1, continent.name());
    }
    for (var territory : territories) {
      var neighbours = risk.world().neighbours(territory);
      assertFalse(neighbours.isEmpty(), territory.name() + " has no neighbours");
      assertFalse(neighbours.contains(territory));
      for (var neighbour : neighbours) {
        assertTrue(risk.areAdjacent(neighbour, territory), territory.name() + " - " + neighbour.name() + " is not symmetric");
      }
    }
    // all territories can be reached from the first
    var visited = new HashSet<>(List.of(territories.getFirst()));
    var queue = new ArrayDeque<>(visited);
    while (! queue.isEmpty()) {
      for (var neighbour : risk.world().neighbours(queue.remove())) {
        if (visited.add(neighbour)) {
          queue.add(neighbour);
        }
      }
    }
    assertEquals(territories.size(), visited.size(), map.name() + " is not connected");
  }

  @ParameterizedTest
  @MethodSource("maps")
  public void aGameCanBeStartedOnEveryMap(GameMap map) {
    for (int players = 2; players <= 4; players++) {
      var risk = new RiskConfig(players).withMap(map).createRisk();
      risk.reset(new java.util.Random(1), 2.5f);
      for (var player : risk.players()) {
        assertTrue(risk.isAlive(player));
      }
    }
  }

  @org.junit.jupiter.api.Test
  public void europeHasTheTerritoriesAndRegionsOfTheDesign() {
    var risk = new RiskConfig(2).withMap(GameMap.EUROPE).createRisk();
    assertEquals(37, risk.allTerritories().size());
    assertEquals(6, risk.continents().size());
    assertEquals(23, risk.continents().stream().mapToInt(Continent::bonusPoints).sum());
    // Iberia is a chain, and the Mediterranean isles are one territory connected by sea to France, Spain and Italy
    assertTrue(risk.areAdjacent(risk.getTerritory("Portugal"), risk.getTerritory("Spain")));
    assertFalse(risk.areAdjacent(risk.getTerritory("Portugal"), risk.getTerritory("France")));
    assertTrue(risk.areAdjacent(risk.getTerritory("England"), risk.getTerritory("France")));
    // the Balkans, where the shapes on the map meet as the borders say
    assertTrue(risk.areAdjacent(risk.getTerritory("Albania"), risk.getTerritory("Serbia")));
    assertTrue(risk.areAdjacent(risk.getTerritory("Montenegro"), risk.getTerritory("Croatia")));
    assertFalse(risk.areAdjacent(risk.getTerritory("Greece"), risk.getTerritory("Serbia")));
    assertEquals(4, risk.world().neighbours(risk.getTerritory("North Macedonia")).size());
    var isles = risk.getTerritory("Mediterranean Isles");
    assertEquals(3, risk.world().neighbours(isles).size());
    assertTrue(risk.areAdjacent(isles, risk.getTerritory("Spain")));
  }
}
