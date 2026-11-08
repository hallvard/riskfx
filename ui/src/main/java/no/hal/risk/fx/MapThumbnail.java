package no.hal.risk.fx;

import java.util.ArrayList;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.Territory;

/**
 * A small picture of a map, for choosing between maps: the shapes of its territories, drawn from the same data as the map itself, each
 * in the color of its region, and the shapes that are on the map but not in the game pale. It is a vector, so it is sharp at any size,
 * and a new map has one without anything more.
 */
public final class MapThumbnail {

  private MapThumbnail() {
  }

  /** The thumbnail of a map, scaled to fit in the width and the height, and centered in them. */
  public static Node of(GameMap map, double width, double height) {
    var risk = new RiskConfig(2).withMap(map).createRisk();
    var paths = new TerritoryPathProvider(map.shapesResource(), null);
    var shapes = new ArrayList<Path>();
    var continents = risk.continents();
    for (int i = 0; i < continents.size(); i++) {
      var color = WorldView.regionColor(i, continents.size());
      for (Territory territory : continents.get(i).territories()) {
        shapes.add(shape(paths.getPath(territory.name()), Color.hsb(color.getHue(), 0.55, 0.92), Color.rgb(60, 60, 60)));
      }
    }
    var territoryNames = risk.allTerritories().stream().map(Territory::name).toList();
    var behind = new ArrayList<Path>();
    for (var inactive : paths.inactive(territoryNames)) {
      behind.add(shape(inactive.path(), Color.rgb(250, 246, 235), Color.rgb(170, 170, 170)));
    }
    var drawing = new Group();
    drawing.getChildren().addAll(behind);
    drawing.getChildren().addAll(shapes);
    drawing.setMouseTransparent(true);

    // fitted into the size, and centered, by the shapes themselves and not by what the group says, which has the lines in it too
    Bounds bounds = drawing.getBoundsInLocal();
    double scale = Math.min(width / bounds.getWidth(), height / bounds.getHeight());
    var fitted = new Group(drawing);
    fitted.getTransforms().addAll(
        new Translate((width - bounds.getWidth() * scale) / 2, (height - bounds.getHeight() * scale) / 2),
        new Scale(scale, scale),
        new Translate(-bounds.getMinX(), -bounds.getMinY()));
    // lines of the same width on the screen, whatever the scale
    drawing.getChildren().forEach(node -> ((Path) node).setStrokeWidth(0.6 / scale));

    var pane = new Pane(fitted);
    pane.setPrefSize(width, height);
    pane.setMinSize(width, height);
    pane.setMaxSize(width, height);
    pane.setMouseTransparent(true);
    return pane;
  }

  private static Path shape(Path original, Color fill, Color stroke) {
    var copy = WorldView.copyOf(original);
    copy.setFill(fill);
    copy.setStroke(stroke);
    return copy;
  }
}
