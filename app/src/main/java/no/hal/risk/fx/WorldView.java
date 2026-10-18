package no.hal.risk.fx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import no.hal.risk.Territory;
import no.hal.risk.World;

public class WorldView {

  private final Pane mapPane;
  private TerritoryPathProvider territoryPathProvider;

  private Map<Territory, Collection<Node>> territoryNodes = new HashMap<>();

  public WorldView(Pane mapPane, TerritoryPathProvider territoryPathProvider) {
    this.mapPane = mapPane;
    this.territoryPathProvider = territoryPathProvider;
    worldProperty.subscribe(this::updateMap);
    mapPane.setOnMousePressed(event -> {
      pressedTerritory = event.getButton() == MouseButton.PRIMARY ? territoryAt(event) : null;
      pressedAt = System.nanoTime();
    });
    mapPane.setOnMouseReleased(event -> {
      var territory = territoryAt(event);
      // a release on another territory than the press was on is a drag, which does nothing
      if (territory != null && territory == pressedTerritory && onTerritoryReleased != null) {
        var release = new TerritoryRelease(territory, System.nanoTime() - pressedAt >= HOLD_NANOS, event.getScreenX(), event.getScreenY());
        Platform.runLater(() -> onTerritoryReleased.accept(release));
      }
      pressedTerritory = null;
    });
  }

  /** How long the mouse button must be held for the release to ask for a menu instead of the default action. */
  public static final long HOLD_NANOS = 350_000_000L;

  /**
   * A mouse button released on a territory, which was also where it was pressed.
   *
   * @param held whether the button was held long, which asks for a menu with the default action first, instead of the default action
   * @param screenX where the mouse was, in screen coordinates
   */
  public record TerritoryRelease(Territory territory, boolean held, double screenX, double screenY) {
  }

  private Territory pressedTerritory;
  private long pressedAt;

  private Territory territoryAt(MouseEvent event) {
    Node node = event.getPickResult().getIntersectedNode();
    return node != null && node.getUserData() instanceof Territory territory ? territory : null;
  }

  public Node node() {
    return mapPane;
  }

  private Property<World> worldProperty = new SimpleObjectProperty<>();

  /** Sets where the shapes of the territories come from, for the next world. */
  public void setTerritoryPathProvider(TerritoryPathProvider territoryPathProvider) {
    this.territoryPathProvider = territoryPathProvider;
  }

  public Property<World> worldProperty() {
    return worldProperty;
  }

  public World getWorld() {
    return worldProperty.getValue();
  }

  private Property<Territory> highlightedTerritoryProperty = new SimpleObjectProperty<>();

  public Property<Territory> highlightedTerritoryProperty() {
    return highlightedTerritoryProperty;
  }

  public Territory getHighlightedTerritory() {
    return highlightedTerritoryProperty.getValue();
  }

  public void setHighlightedTerritory(Territory territory) {
    highlightedTerritoryProperty.setValue(territory);
  }

  private Consumer<TerritoryRelease> onTerritoryReleased = null;

  public void setOnTerritoryReleased(Consumer<TerritoryRelease> onTerritoryReleased) {
    this.onTerritoryReleased = onTerritoryReleased;
  }

  private void addTerritoryNodes(Territory territory, Node... nodes) {
    territoryNodes.computeIfAbsent(territory, t -> new ArrayList<>()).addAll(List.of(nodes));
  }

  private void updateMap() {
    territoryNodes.clear();
    var children = mapPane.getChildren();
    children.clear();
    // create paths
    var paths = getWorld().allTerritories().stream()
        .map(territory -> {
          var path = territoryPathProvider.getPath(territory.name());
          addTerritoryNodes(territory, path);
          updateTerritoryPath(territory);
          path.setUserData(territory);
          // the name is only in the tooltip, since the label shows the armies
          Tooltip.install(path, new Tooltip(territory.name()));
          return path;
        })
        .toList();
    // the shapes that aren't territories of the game are first, behind everything, grey and without any reaction to the mouse
    inactiveNames.clear();
    for (var inactive : territoryPathProvider.inactive(getWorld().allTerritories().stream().map(Territory::name).toList())) {
      var shape = copyOf(inactive.path());
      // pale and dashed, so they aren't mistaken for a player: they must differ from all the player colors, the grey one too
      shape.setFill(Color.rgb(250, 246, 235));
      shape.setStroke(Color.rgb(170, 170, 170));
      shape.setStrokeWidth(0.8);
      shape.getStrokeDashArray().addAll(3.0, 3.0);
      Tooltip.install(shape, new Tooltip(inactive.name() + " (not in the game)"));
      children.add(shape);
      if (inactive.labelPoint() != null) {
        var name = new Text(inactive.name());
        name.setFont(Font.font(9));
        name.setFill(Color.GRAY);
        name.setMouseTransparent(true);
        var width = name.getLayoutBounds().getWidth();
        name.setLayoutX(inactive.labelPoint().getX() - width / 2);
        name.setLayoutY(inactive.labelPoint().getY());
        // as for the names of territories, one that is wider than its shape would cover the neighbours
        name.getProperties().put("fits", width <= shape.getBoundsInLocal().getWidth() * 0.95);
        inactiveNames.add(name);
        children.add(name);
      }
    }
    // add borders first, so they are drawn behind the territories and only show where territories don't meet
    children.addAll(createBorderLines(paths));
    children.addAll(paths);
    // add labels: the armies, and the name below it
    children.addAll(getWorld().allTerritories().stream()
        .map(territory -> {
          var label = new Text();
          addTerritoryNodes(territory, label);
          updateTerritoryLabel(territory);
          label.setUserData(territory);
          return label;
        })
        .toList()
    );
    nameLabels.clear();
    for (var territory : getWorld().allTerritories()) {
      var name = new Text(territory.name());
      name.setFont(Font.font(9));
      name.setFill(Color.rgb(30, 30, 30));
      name.setUserData(territory);
      nameLabels.put(territory, name);
      children.add(name);
      positionNameLabel(territory);
    }
    // the regions get a colored rim on top, but don't take the mouse
    regionNodes.clear();
    var continents = getWorld().continents();
    for (int i = 0; i < continents.size(); i++) {
      var continent = continents.get(i);
      var color = Color.hsb(360.0 * i / continents.size(), 0.85, 0.55);
      // a rim inside each territory in the color of its region, so borders between regions have two colors,
      // and those within a region have the same color on both sides
      for (var territory : continent.territories()) {
        var path = getTerritoryNode(territory, Path.class);
        var rim = copyOf(path);
        rim.setFill(Color.TRANSPARENT);
        rim.setStroke(color);
        rim.setStrokeWidth(3);
        rim.setStrokeType(StrokeType.INSIDE);
        rim.setMouseTransparent(true);
        regionNodes.add(rim);
        children.add(rim);
      }
    }
    updateVisibility();
  }

  /** A new path with the same shape, since the elements of a path should not be shared. */
  private static Path copyOf(Path path) {
    var copy = new Path();
    for (var element : path.getElements()) {
      switch (element) {
        case MoveTo move -> copy.getElements().add(new MoveTo(move.getX(), move.getY()));
        case LineTo line -> copy.getElements().add(new LineTo(line.getX(), line.getY()));
        case CubicCurveTo curve -> copy.getElements().add(new CubicCurveTo(curve.getControlX1(), curve.getControlY1(),
            curve.getControlX2(), curve.getControlY2(), curve.getX(), curve.getY()));
        case ClosePath _ -> copy.getElements().add(new ClosePath());
        default -> throw new IllegalArgumentException("Unknown path element " + element);
      }
    }
    return copy;
  }

  private final Map<Territory, Text> nameLabels = new HashMap<>();
  private final List<Text> inactiveNames = new ArrayList<>();
  private final List<Node> regionNodes = new ArrayList<>();
  private boolean showNames = true;
  private boolean showRegions = false;

  /** Whether the name of each territory is shown, below the number of armies. */
  public void setShowNames(boolean showNames) {
    this.showNames = showNames;
    updateVisibility();
  }

  /** Whether each territory has a rim in the color of its region. */
  public void setShowRegions(boolean showRegions) {
    this.showRegions = showRegions;
    updateVisibility();
  }

  private void updateVisibility() {
    nameLabels.values().forEach(label -> label.setVisible(showNames && Boolean.TRUE.equals(label.getProperties().get("fits"))));
    inactiveNames.forEach(label -> label.setVisible(showNames && Boolean.TRUE.equals(label.getProperties().get("fits"))));
    regionNodes.forEach(node -> node.setVisible(showRegions));
    raiseRegionNodes();
  }

  /** The names of the territories, and the rims and names of the regions, are above the territories that were raised to show an outline. */
  private void raiseRegionNodes() {
    nameLabels.values().forEach(Node::toFront);
    regionNodes.forEach(Node::toFront);
  }

  private void positionNameLabel(Territory territory) {
    var name = nameLabels.get(territory);
    if (name == null) {
      return;
    }
    var center = center(territory);
    var armies = getTerritoryNode(territory, Text.class).getLayoutBounds();
    var nameBounds = name.getLayoutBounds();
    name.setLayoutX(center.getX() - nameBounds.getWidth() / 2);
    name.setLayoutY(center.getY() + armies.getHeight() / 2 + 2);
    // a name that is wider than its territory would cover its neighbours, and is in the tooltip
    var pathBounds = getTerritoryNode(territory, Path.class).getBoundsInLocal();
    name.getProperties().put("fits", nameBounds.getWidth() <= pathBounds.getWidth() * 0.95);
    name.setVisible(showNames && Boolean.TRUE.equals(name.getProperties().get("fits")));
  }

  /** Where the labels of a territory are, and the connections to it end: where the map says, and otherwise the middle of its shape. */
  private Point2D center(Territory territory) {
    var label = territoryPathProvider.getLabelPoint(territory.name());
    if (label != null) {
      return label;
    }
    var bounds = getTerritoryNode(territory, Path.class).getBoundsInLocal();
    return new Point2D(bounds.getCenterX(), bounds.getCenterY());
  }

  /**
   * Dashed lines between the centers of all adjacent territories, to show the connections across sea.
   * A connection across the map edge (e.g. Alaska - Kamchatka) is drawn as two lines, going off each edge of the map.
   */
  private List<Line> createBorderLines(List<Path> paths) {
    var minX = paths.stream().mapToDouble(path -> path.getBoundsInLocal().getMinX()).min().orElse(0);
    var maxX = paths.stream().mapToDouble(path -> path.getBoundsInLocal().getMaxX()).max().orElse(0);
    var width = maxX - minX;
    var lines = new ArrayList<Line>();
    for (var territory : getWorld().allTerritories()) {
      for (var neighbour : getWorld().neighbours(territory)) {
        // only once for each pair
        if (territory.name().compareTo(neighbour.name()) >= 0) {
          continue;
        }
        // territories that meet on the map need no line, only those that are apart, as across sea
        if (territoryPathProvider.meet(territory.name(), neighbour.name())) {
          continue;
        }
        var from = center(territory);
        var to = center(neighbour);
        if (Math.abs(from.getX() - to.getX()) > width / 2) {
          var left = from.getX() < to.getX() ? from : to;
          var right = left == from ? to : from;
          // continue each line off the edge, towards where the other territory appears on the other side
          lines.add(borderLine(left, new Point2D(right.getX() - width, right.getY()), minX));
          lines.add(borderLine(right, new Point2D(left.getX() + width, left.getY()), maxX));
        } else {
          lines.add(borderLine(from, to, null));
        }
      }
    }
    return lines;
  }

  /** A line from a point towards another, optionally stopping at a given x coordinate. */
  private Line borderLine(Point2D from, Point2D towards, Double stopX) {
    var to = towards;
    if (stopX != null && towards.getX() != from.getX()) {
      var t = (stopX - from.getX()) / (towards.getX() - from.getX());
      to = new Point2D(stopX, from.getY() + t * (towards.getY() - from.getY()));
    }
    var line = new Line(from.getX(), from.getY(), to.getX(), to.getY());
    line.setStroke(Color.DARKSLATEGRAY);
    line.setStrokeWidth(1.5);
    line.getStrokeDashArray().addAll(6.0, 4.0);
    line.setMouseTransparent(true);
    return line;
  }

  private <T extends Node> T getTerritoryNode(Territory territory, Class<T> nodeType) {
    return territoryNodes.get(territory).stream()
        .filter(nodeType::isInstance)
        .map(nodeType::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No node of type " + nodeType.getSimpleName() + " for territory: " + territory.name()));
  }

  private final Map<Territory, Color> markedTerritories = new HashMap<>();

  /**
   * Emphasizes some territories with an outline of a given color, e.g. those involved in the last move,
   * and removes the emphasis from the others.
   */
  public void setMarkedTerritories(Map<Territory, Color> territories) {
    var changed = new java.util.HashSet<>(markedTerritories.keySet());
    changed.addAll(territories.keySet());
    markedTerritories.clear();
    markedTerritories.putAll(territories);
    for (var territory : changed) {
      if (territoryNodes.containsKey(territory)) {
        updateTerritoryPath(territory);
        if (markedTerritories.containsKey(territory)) {
          // above the neighbours, which would cover the outline, but not above the label
          getTerritoryNode(territory, Path.class).toFront();
          getTerritoryNode(territory, Text.class).toFront();
        }
      }
    }
    raiseRegionNodes();
  }

  public void updateTerritory(Territory territory) {
    updateTerritoryPath(territory);
    updateTerritoryLabel(territory);
  }

  private void updateTerritoryPath(Territory territory) {
    var path = getTerritoryNode(territory, Path.class);
    Color fillColor = FxColors.of(territory.occupant().color());
    path.setFill(fillColor);
    var markColor = markedTerritories.get(territory);
    path.setStroke(markColor != null ? markColor : Color.BLACK);
    path.setStrokeWidth(markColor != null ? 4 : 1);
    // outside the territory, so an outline is visible even if it has the color of the occupant
    path.setStrokeType(markColor != null ? StrokeType.OUTSIDE : StrokeType.CENTERED);
  }

  private void updateTerritoryLabel(Territory territory) {
    var label = getTerritoryNode(territory, Text.class);
    String labelString = territory.name();
    if (territory.numArmies() > 0) {
      labelString = String.valueOf(territory.numArmies());
    }
    label.setText(labelString);
    var path = getTerritoryNode(territory, Path.class);
    var center = center(territory);
    var nameBounds = label.getLayoutBounds();
    label.setLayoutX(center.getX() - nameBounds.getWidth() / 2);
    label.setLayoutY(center.getY() - nameBounds.getHeight() / 2);
    positionNameLabel(territory);
  }
}
