package no.hal.risk;

import java.nio.file.Path;
import java.util.List;

/**
 * A map to play on: the files with its continents and borders, which are resources of the core, and the file with the shapes
 * of the territories for the user interface, which is a resource of the application. A map can also have a file, in the source tree of
 * the project, where the places of the labels that have been moved by hand are saved, or null.
 * A map with the shapes written by a generator has the displacements applied by it. For one without, the application applies them, from
 * the resource with the displacements, or null if the generator does, and this file is where that resource comes from.
 */
public record GameMap(String name, Path continentsPath, Path bordersPath, String shapesResource, String labelOffsetsFile,
    String labelOffsetsResource) {

  public static final GameMap CLASSIC = new GameMap("Classic", Path.of("/continents.json"), Path.of("/borders.json"), "/map.json",
      "app/src/main/resources/label-offsets.json", "/label-offsets.json");

  public static final GameMap EUROPE = new GameMap("Europe", Path.of("/maps/europe/continents.json"), Path.of("/maps/europe/borders.json"),
      "/maps/europe/map.json", "tools/europe/label-offsets.json", null);

  public static List<GameMap> all() {
    return List.of(CLASSIC, EUROPE);
  }

  @Override
  public String toString() {
    return name;
  }
}
