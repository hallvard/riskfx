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
import javafx.geometry.Point2D;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

@ApplicationScoped
public class TerritoryPathProvider {

  private Map<String, Path> territoryPaths;
  private final Map<String, Point2D> labelPoints = new HashMap<>();
  private final Map<String, String> names = new HashMap<>();
  private final Map<String, Point2D> labelBases = new HashMap<>();
  private final Map<String, java.util.Set<String>> touching = new HashMap<>();
  private final String shapesResource;

  private final String offsetsResource;
  private final Map<String, Point2D> labelOffsets = new HashMap<>();

  /** The shapes of the classic map. */
  public TerritoryPathProvider() {
    this("/map.json", "/label-offsets.json");
  }

  /** @param shapesResource a resource with the path of each territory */
  public TerritoryPathProvider(String shapesResource) {
    this(shapesResource, null);
  }

  /**
   * @param shapesResource a resource with the path of each territory
   * @param offsetsResource a resource that moves the labels from where they are by default, as an object with the displacement of
   *   each territory by name, or null, or one that doesn't exist, for none
   */
  public TerritoryPathProvider(String shapesResource, String offsetsResource) {
    this.shapesResource = shapesResource;
    this.offsetsResource = offsetsResource;
  }

  /** The displacement of the label of a territory from where it is by default, which is nothing if the map has none. */
  public Point2D getLabelOffset(String territoryName) {
    territoryPaths();
    return labelOffsets.getOrDefault(nameToId(territoryName), Point2D.ZERO);
  }

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

  /** Where the armies and the name of a territory are shown, if the map says, and otherwise null. */
  public Point2D getLabelPoint(String territoryName) {
    territoryPaths();
    return labelPoints.get(nameToId(territoryName));
  }

  /** Where the label would be without the displacement by hand, if the map says, and otherwise null. */
  public Point2D getLabelBase(String territoryName) {
    territoryPaths();
    return labelBases.get(nameToId(territoryName));
  }

  /**
   * Whether two territories meet on the map, if the map says so, which then needs no line between them.
   * A map that doesn't say has a line for each pair of neighbours.
   */
  public boolean meet(String territoryName, String otherName) {
    territoryPaths();
    var others = touching.get(nameToId(territoryName));
    return others != null && others.contains(nameToId(otherName));
  }

  /** A shape that is on the map, but is not a territory of the game, and is shown as inactive, such as a neutral country. */
  public record Inactive(String name, Path path, Point2D labelPoint) {
  }

  /** The shapes of the map that are not for any of the territories, which are those that are in a region of the game. */
  public java.util.List<Inactive> inactive(Collection<String> territoryNames) {
    var active = territoryNames.stream().map(this::nameToId).collect(java.util.stream.Collectors.toSet());
    return territoryPaths().entrySet().stream()
        .filter(entry -> ! active.contains(entry.getKey()))
        .map(entry -> new Inactive(names.getOrDefault(entry.getKey(), idToName(entry.getKey())), entry.getValue(), labelPoints.get(entry.getKey())))
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
    loadLabelOffsets();
    JsonNode jsonNode = objectMapper.readTree(getClass().getResourceAsStream(shapesResource));
    if (jsonNode instanceof ArrayNode arrayNode) {
      for (JsonNode node : arrayNode) {
        if (node instanceof ObjectNode pathNode) {
          var id = pathNode.get("id").asText();
          var pathString = pathNode.get("path").asText();
          territoryPaths.put(id, parsePathString(pathString));
          if (pathNode.has("name")) {
            names.put(id, pathNode.get("name").asText());
          }
          if (pathNode.get("label") instanceof ArrayNode label && label.size() == 2) {
            labelPoints.put(id, new Point2D(label.get(0).asDouble(), label.get(1).asDouble()));
          }
          if (pathNode.get("labelBase") instanceof ArrayNode base && base.size() == 2) {
            labelBases.put(id, new Point2D(base.get(0).asDouble(), base.get(1).asDouble()));
          }
          if (pathNode.get("touches") instanceof ArrayNode touches) {
            var ids = new java.util.HashSet<String>();
            touches.forEach(other -> ids.add(nameToId(other.asText())));
            touching.put(id, ids);
          }
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

  private void loadLabelOffsets() throws IOException {
    if (offsetsResource == null) {
      return;
    }
    try (var stream = getClass().getResourceAsStream(offsetsResource)) {
      if (stream != null && objectMapper.readTree(stream) instanceof ObjectNode offsets) {
        offsets.properties().forEach(entry -> {
          if (entry.getValue() instanceof ArrayNode displacement && displacement.size() == 2) {
            labelOffsets.put(nameToId(entry.getKey()), new Point2D(displacement.get(0).asDouble(), displacement.get(1).asDouble()));
          }
        });
      }
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
