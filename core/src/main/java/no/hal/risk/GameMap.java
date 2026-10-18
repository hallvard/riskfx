package no.hal.risk;

import java.nio.file.Path;
import java.util.List;

/**
 * A map to play on: the files with its continents and borders, which are resources of the core, and the file with the shapes
 * of the territories for the user interface, which is a resource of the application.
 */
public record GameMap(String name, Path continentsPath, Path bordersPath, String shapesResource) {

  public static final GameMap CLASSIC = new GameMap("Classic", Path.of("/continents.json"), Path.of("/borders.json"), "/map.json");

  public static final GameMap EUROPE = new GameMap("Europe", Path.of("/maps/europe/continents.json"), Path.of("/maps/europe/borders.json"),
      "/maps/europe/map.json");

  public static List<GameMap> all() {
    return List.of(CLASSIC, EUROPE);
  }

  @Override
  public String toString() {
    return name;
  }
}
