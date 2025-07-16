package no.hal.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javafx.scene.paint.Color;

public record RiskConfig(
    Path continentsPath,
    float initialArmiesPrTerritory,
    int playerCount
  ) {

  private static Color[] colors = {
      Color.ORANGE, Color.LIGHTBLUE, Color.GREEN, Color.YELLOW, Color.PURPLE, Color.ORANGE
  };

  public RiskConfig(int playerCount) {
    this(Path.of("/continents.json"), 2.5f, playerCount);
  }

  public RiskConfig withPlayerCount(int playerCount) {
    return new RiskConfig(continentsPath, initialArmiesPrTerritory, playerCount);
  }

  public Risk createRisk() {
    List<Continent> continents = loadContinents();
    List<Player> players = IntStream.range(0, playerCount)
        .mapToObj(i -> new Player("Player " + (i + 1), colors[i]))
        .toList();
    var risk = new Risk(continents, players);
    risk.distributeInitialArmies(initialArmiesPrTerritory);
    return risk;
  }

  private static ObjectMapper objectMapper = new ObjectMapper();

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
