package no.hal.risk.fx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

@ApplicationScoped
public class TerritoryPathProvider {

  private Map<String, Path> territoryPaths;

  public Map<String, Path> territoryPaths() {
    if (territoryPaths == null) {
      try {
        loadTerritoryPaths();
      } catch (IOException e) {
        // ignore
      }
    }
    return territoryPaths;
  }

  public Collection<String> getTerritoryNames() {
    return territoryPaths().keySet().stream()
        .map(this::idToName)
        .toList();
  }

  public Path getPath(String territoryName) {
    return territoryPaths().get(nameToId(territoryName));
  }

  protected String nameToId(String name) {
    return name.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }

  protected String idToName(String id) {
    var name = new StringBuilder();
    boolean nextUpper = true;
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c == '_') {
        name.append(' ');
        nextUpper = true;
      } else {
        if (nextUpper) {
          c = Character.toUpperCase(c);
          nextUpper = false;
        }
        name.append(c);
      }
    }
    return name.toString();
  }

  private ObjectMapper objectMapper = new ObjectMapper();

  private void loadTerritoryPaths() throws IOException{
    territoryPaths = new HashMap<>();
    JsonNode jsonNode = objectMapper.readTree(getClass().getResourceAsStream("/map.json"));
    if (jsonNode instanceof ArrayNode arrayNode) {
      for (JsonNode node : arrayNode) {
        if (node instanceof ObjectNode pathNode) {
          var id = pathNode.get("id").asText();
          var pathString = pathNode.get("path").asText();
          territoryPaths.put(id, parsePathString(pathString));
        }
      }
    } else if (jsonNode instanceof ObjectNode objectNode) {
      var fieldNames = objectNode.fieldNames();
      while (fieldNames.hasNext()) {
        var id = fieldNames.next();
        var pathString = objectNode.get(id).asText();
        territoryPaths.put(id, parsePathString(pathString));
      }
    } else {
      throw new IOException("Invalid JSON format for territory paths");
    }
  }

  private Path parsePathString(String pathString) {
    Path path = new Path();
    StringTokenizer tokenizer = new StringTokenizer(pathString, " ");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      switch (token.toUpperCase()) {
        case "M" -> {
          double[] xy = parseCoordinates(tokenizer.nextToken());
          path.getElements().add(new MoveTo(xy[0], xy[1]));
        }
        case "L" -> {
          double[] xy = parseCoordinates(tokenizer.nextToken());
          path.getElements().add(new LineTo(xy[0], xy[1]));
        }
        case "C" -> {
          double[] xy1 = parseCoordinates(tokenizer.nextToken());
          double[] xy2 = parseCoordinates(tokenizer.nextToken());
          double[] xy = parseCoordinates(tokenizer.nextToken());
          path.getElements().add(new CubicCurveTo(xy1[0], xy1[1], xy2[0], xy2[1], xy[0], xy[1]));
        }
        case "Z" -> path.getElements().add(new ClosePath());
        default -> throw new IllegalArgumentException("Unknown path command: " + token);
      }
    }
    return path;
  }

  private double[] parseCoordinates(String coordinates) {
    String[] coordinateStrings = coordinates.split(",");
    double[] coordinateDoubles = new double[coordinateStrings.length];
    for (int i = 0; i < coordinateStrings.length; i++) {
      coordinateDoubles[i] = Double.parseDouble(coordinateStrings[i].trim());
    }
    return coordinateDoubles;
  }
}
