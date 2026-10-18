package no.hal.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public record RiskConfig(
    Path continentsPath,
    Path bordersPath,
    float initialArmiesPrTerritory,
    int playerCount,
    FortifyMode fortifyMode,
    Rules rules
  ) {

  /**
   * Player colors that contrast with the colors used to mark the last move in the user interface
   * (green, red, orange, yellow and violet), and are light enough for black text to be readable.
   */
  private static final PlayerColor[] colors = {
      PlayerColor.rgb(0xA0724A), // brown
      PlayerColor.rgb(0x6495ED), // blue
      PlayerColor.rgb(0xE6A8D7), // pink
      PlayerColor.rgb(0xBEBEBE), // gray
      PlayerColor.rgb(0x5FBFAF), // teal
      PlayerColor.rgb(0xD2B48C)  // tan
  };

  /** The color of a player, by index from 0. */
  public static PlayerColor playerColor(int index) {
    return colors[index];
  }

  public RiskConfig(int playerCount) {
    this(Path.of("/continents.json"), Path.of("/borders.json"), 2.5f, playerCount, FortifyMode.ADJACENT, Rules.DEFAULT);
  }

  public RiskConfig withPlayerCount(int playerCount) {
    return new RiskConfig(continentsPath, bordersPath, initialArmiesPrTerritory, playerCount, fortifyMode, rules);
  }

  public RiskConfig withFortifyMode(FortifyMode fortifyMode) {
    return new RiskConfig(continentsPath, bordersPath, initialArmiesPrTerritory, playerCount, fortifyMode, rules);
  }

  /** Plays on another map. */
  public RiskConfig withMap(GameMap map) {
    return new RiskConfig(map.continentsPath(), map.bordersPath(), initialArmiesPrTerritory, playerCount, fortifyMode, rules);
  }

  public RiskConfig withRules(Rules rules) {
    return new RiskConfig(continentsPath, bordersPath, initialArmiesPrTerritory, playerCount, fortifyMode, rules);
  }

  public Risk createRisk() {
    List<Continent> continents = loadContinents();
    List<Player> players = IntStream.range(0, playerCount)
        .mapToObj(i -> new Player("Player " + (i + 1), colors[i]))
        .toList();
    var risk = new Risk(continents, loadBorders(continents), players, fortifyMode, rules);
    risk.distributeInitialArmies(initialArmiesPrTerritory);
    return risk;
  }

  private static ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Loads the borders as an object mapping each territory name to the names of its neighbours.
   * Borders are not computed from the map polygons, since some neighbours are separated by sea.
   */
  private Map<Territory, Set<Territory>> loadBorders(List<Continent> continents) {
    Map<String, Territory> territories = new HashMap<>();
    continents.stream()
        .flatMap(continent -> continent.territories().stream())
        .forEach(territory -> territories.put(territory.name(), territory));
    Map<Territory, Set<Territory>> borders = new HashMap<>();
    try (var inputStream = getClass().getResourceAsStream(bordersPath.toString())) {
      var bordersNode = objectMapper.readTree(inputStream);
      for (var entry : bordersNode.properties()) {
        var territory = lookupTerritory(territories, entry.getKey());
        for (JsonNode neighbourNode : entry.getValue()) {
          var neighbour = lookupTerritory(territories, neighbourNode.asText());
          borders.computeIfAbsent(territory, _ -> new HashSet<>()).add(neighbour);
          borders.computeIfAbsent(neighbour, _ -> new HashSet<>()).add(territory);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not load borders from " + bordersPath, e);
    }
    return borders;
  }

  private static Territory lookupTerritory(Map<String, Territory> territories, String name) {
    var territory = territories.get(name);
    if (territory == null) {
      throw new IllegalStateException("Unknown territory in borders: " + name);
    }
    return territory;
  }

  private List<Continent> loadContinents() {
    try (var inputStream = getClass().getResourceAsStream(continentsPath.toString())) {
        return loadContinents(objectMapper.readTree(inputStream));
    } catch (Exception e) {
      return null;
    }
  }

  private List<Continent> loadContinents(JsonNode jsonNode) {
    List<Continent> continents = new ArrayList<>();
    if (jsonNode instanceof ArrayNode arrayNode) {
      for (JsonNode node : arrayNode) {
        if (node instanceof ObjectNode continentNode) {
          var name = continentNode.get("name").asText();
          var bonusPoints = continentNode.get("bonusPoints").asInt();
          var territoryNames = continentNode.get("territories");
          List<Territory> territories = new ArrayList<>();
          if (territoryNames instanceof ArrayNode territoryNamesArrayNode) {
            for (JsonNode territoryNameNode : territoryNamesArrayNode) {
              territories.add(new Territory(territoryNameNode.asText()));
            }
          } else {
            continue; 
          }
          continents.add(new Continent(name, bonusPoints, territories));
        }
      }
    } else {
      return null;
    }
    return continents;
  }
}
