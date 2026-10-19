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
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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
      if (editLabels) {
        draggedLabel = event.getButton() == MouseButton.PRIMARY ? territoryAt(event) : null;
        dragFrom = new Point2D(event.getX(), event.getY());
        pressedTerritory = null;
        return;
      }
      pressedTerritory = event.getButton() == MouseButton.PRIMARY ? territoryAt(event) : null;
      pressedAt = System.nanoTime();
    });
    mapPane.setOnMouseDragged(event -> {
      if (editLabels && draggedLabel != null) {
        // the coordinates are those of the map, whatever the zoom
        var to = new Point2D(event.getX(), event.getY());
        labelOffsets.merge(draggedLabel, to.subtract(dragFrom), Point2D::add);
        dragFrom = to;
        moveLabel(draggedLabel);
      }
    });
    mapPane.setOnMouseClicked(event -> {
      // a double click puts a label back
      if (editLabels && event.getClickCount() == 2 && territoryAt(event) != null) {
        labelOffsets.remove(territoryAt(event));
        moveLabel(territoryAt(event));
      }
    });
    mapPane.setOnMouseReleased(event -> {
      if (editLabels) {
        draggedLabel = null;
        return;
      }
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

  // editing the places of the labels: the displacement of each, from where the map has it by default
  private boolean editLabels;
  private final Map<Territory, Point2D> labelOffsets = new HashMap<>();
  private Territory draggedLabel;
  private Point2D dragFrom;
  private final Group lineLayer = new Group();
  private final Group editMarkers = new Group();
  private final Map<Territory, Circle> editMarkerOf = new HashMap<>();
  private List<Path> shapes = new ArrayList<>();

  /** Whether pressing and dragging a territory moves its label (the number of armies and the name), instead of playing. */
  public void setEditLabels(boolean editLabels) {
    this.editLabels = editLabels;
    draggedLabel = null;
    editMarkers.setVisible(editLabels);
  }

  public boolean isEditingLabels() {
    return editLabels;
  }

  /** The displacements of the labels from where the map has them by default, by territory name, for those that are moved. */
  public Map<String, Point2D> labelOffsets() {
    var result = new java.util.TreeMap<String, Point2D>();
    labelOffsets.forEach((territory, offset) -> {
      if (offset.magnitude() > 0.05) {
        result.put(territory.name(), offset);
      }
    });
    return result;
  }

  /** Puts the labels at the given displacements, by territory name, and the others where the map has them by default. */
  public void setLabelOffsets(Map<String, Point2D> offsets) {
    labelOffsets.clear();
    if (getWorld() != null) {
      getWorld().allTerritories().stream().filter(territory -> offsets.containsKey(territory.name()))
          .forEach(territory -> labelOffsets.put(territory, offsets.get(territory.name())));
      getWorld().allTerritories().forEach(this::moveLabel);
    }
  }

  /** After a label has moved: its text, the lines to it and the marker. */
  private void moveLabel(Territory territory) {
    updateTerritoryLabel(territory);
    lineLayer.getChildren().setAll(createBorderLines(shapes));
    applyLineWidths();
    placeEditMarker(territory);
  }

  private void placeEditMarker(Territory territory) {
    var marker = editMarkerOf.get(territory);
    if (marker != null) {
      var center = center(territory);
      marker.setCenterX(center.getX());
      marker.setCenterY(center.getY());
      marker.setRadius(9 / viewScale);
      marker.setStrokeWidth(1.5 / viewScale);
    }
  }

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
    // the shapes that aren't territories of the game are first, behind everything, pale and with names, and without any reaction to the mouse
    inactiveShapes.clear();
    inactiveNames.clear();
    for (var inactive : territoryPathProvider.inactive(getWorld().allTerritories().stream().map(Territory::name).toList())) {
      var shape = copyOf(inactive.path());
      // pale and dashed, so they aren't mistaken for a player: they must differ from all the player colors, the grey one too
      shape.setFill(Color.rgb(250, 246, 235));
      shape.setStroke(Color.rgb(170, 170, 170));
      Tooltip.install(shape, new Tooltip(inactive.name() + " (not in the game)"));
      inactiveShapes.add(shape);
      children.add(shape);
      if (inactive.labelPoint() != null) {
        var name = new Text(inactive.name());
        name.setFont(NAME_FONT);
        name.setFill(Color.GRAY);
        name.setMouseTransparent(true);
        name.getProperties().put("full", inactive.name());
        name.getProperties().put("center", inactive.labelPoint());
        name.getProperties().put("shape", shape);
        inactiveNames.add(name);
        children.add(name);
      }
    }
    // add borders first, so they are drawn behind the territories and only show where territories don't meet
    shapes = paths;
    lineLayer.setMouseTransparent(true);
    lineLayer.getChildren().setAll(createBorderLines(paths));
    children.add(lineLayer);
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
      name.setFont(NAME_FONT);
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
        rim.setStrokeWidth(RIM_WIDTH);
        rim.setStrokeType(StrokeType.INSIDE);
        rim.setMouseTransparent(true);
        regionNodes.add(rim);
        children.add(rim);
      }
    }
    // markers on the labels, for dragging them, shown when editing
    editMarkers.getChildren().clear();
    editMarkerOf.clear();
    editMarkers.setMouseTransparent(true);
    editMarkers.setVisible(editLabels);
    for (var territory : getWorld().allTerritories()) {
      var marker = new Circle();
      marker.setFill(Color.rgb(255, 0, 0, 0.15));
      marker.setStroke(Color.RED);
      editMarkerOf.put(territory, marker);
      editMarkers.getChildren().add(marker);
    }
    children.add(editMarkers);
    // the labels that were moved by hand, as the map says
    labelOffsets.clear();
    for (var territory : getWorld().allTerritories()) {
      var base = territoryPathProvider.getLabelBase(territory.name());
      var label = territoryPathProvider.getLabelPoint(territory.name());
      if (base != null && label != null) {
        labelOffsets.put(territory, label.subtract(base));
      } else {
        // a map without a generator has the displacements in a file of their own
        labelOffsets.put(territory, territoryPathProvider.getLabelOffset(territory.name()));
      }
    }
    applyViewScale();
  }

  private static final Font NAME_FONT = Font.font(10.5);
  private static final double RIM_WIDTH = 3;
  /** The share of the width of a territory that a name may take. */
  private static final double NAME_SHARE = 0.92;
  /** The fewest letters of a name that are worth showing before the ellipsis. */
  private static final int MIN_LETTERS = 3;

  /** The text grows a little with the zoom, by the power of this, so it is still readable zoomed out and doesn't get huge zoomed in. */
  private static final double TEXT_ZOOM_EXPONENT = 0.3;
  /** The scale of the map at which the text is its nominal size. */
  private static final double TEXT_REFERENCE_SCALE = 0.8;
  private static final double MAX_TEXT_ZOOM = 1.6;

  private double viewScale = 1;

  /** How much larger the text is than its nominal size, at the present zoom. */
  private double textZoom() {
    return Math.min(MAX_TEXT_ZOOM, Math.pow(viewScale / TEXT_REFERENCE_SCALE, TEXT_ZOOM_EXPONENT));
  }
  private final List<Path> inactiveShapes = new ArrayList<>();

  /**
   * How many pixels on the screen a unit of the map is. Lines keep their width on the screen as the map is zoomed, and the text
   * hardly changes its size, so zooming in gives room for more of the name of each territory, and it is cut to fit, with an
   * ellipsis, or left out.
   */
  public void setViewScale(double scale) {
    if (scale > 0 && scale != viewScale) {
      viewScale = scale;
      applyViewScale();
    }
  }

  private void applyViewScale() {
    if (getWorld() == null || territoryNodes.isEmpty()) {
      return;
    }
    for (var territory : getWorld().allTerritories()) {
      updateTerritoryPath(territory);
      updateTerritoryLabel(territory);
    }
    applyLineWidths();
    editMarkerOf.keySet().forEach(this::placeEditMarker);
    regionNodes.forEach(node -> ((Path) node).setStrokeWidth(RIM_WIDTH / viewScale));
    inactiveShapes.forEach(shape -> {
      shape.setStrokeWidth(0.8 / viewScale);
      shape.getStrokeDashArray().setAll(3.0 / viewScale, 3.0 / viewScale);
    });
    inactiveNames.forEach(this::placeInactiveName);
    updateVisibility();
  }

  private void applyLineWidths() {
    lineLayer.getChildren().forEach(node -> {
      var line = (Line) node;
      line.setStrokeWidth(1.5 / viewScale);
      line.getStrokeDashArray().setAll(6.0 / viewScale, 4.0 / viewScale);
    });
  }

  /** Puts the center of the text at a point, at the same size on the screen whatever the zoom. */
  private void centerAt(Text text, double x, double y) {
    var bounds = text.getLayoutBounds();
    // the map is scaled by the zoom, so the text is scaled back, to the size it should have on the screen
    text.setScaleX(textZoom() / viewScale);
    text.setScaleY(textZoom() / viewScale);
    text.setLayoutX(x - (bounds.getMinX() + bounds.getWidth() / 2));
    text.setLayoutY(y - (bounds.getMinY() + bounds.getHeight() / 2));
  }

  private static double width(String text) {
    var measure = new Text(text);
    measure.setFont(NAME_FONT);
    return measure.getLayoutBounds().getWidth();
  }

  /** The whole name if it fits in the given width on the screen, or its first letters and an ellipsis, or null if even that doesn't fit. */
  private static String fit(String name, double available) {
    if (width(name) <= available) {
      return name;
    }
    String best = null;
    for (int letters = MIN_LETTERS; letters < name.length(); letters++) {
      var shortened = name.substring(0, letters).stripTrailing() + "\u2026";
      if (width(shortened) > available) {
        break;
      }
      best = shortened;
    }
    return best;
  }

  /** The length of the stretch of a horizontal line through a point that is inside the shape, in the units of the map, 0 if it is outside. */
  private static double chordAt(Path shape, double x, double y) {
    var crossings = new ArrayList<Double>();
    double startX = 0, startY = 0, currentX = 0, currentY = 0;
    for (var element : shape.getElements()) {
      double nextX, nextY;
      switch (element) {
        case MoveTo move -> {
          startX = currentX = move.getX();
          startY = currentY = move.getY();
          continue;
        }
        case LineTo line -> {
          nextX = line.getX();
          nextY = line.getY();
        }
        case CubicCurveTo curve -> {
          nextX = curve.getX();
          nextY = curve.getY();
        }
        case ClosePath _ -> {
          nextX = startX;
          nextY = startY;
        }
        default -> {
          continue;
        }
      }
      if ((currentY > y) != (nextY > y)) {
        crossings.add(currentX + (y - currentY) * (nextX - currentX) / (nextY - currentY));
      }
      currentX = nextX;
      currentY = nextY;
    }
    crossings.sort(null);
    for (int i = 0; i + 1 < crossings.size(); i += 2) {
      if (crossings.get(i) <= x && x <= crossings.get(i + 1)) {
        return crossings.get(i + 1) - crossings.get(i);
      }
    }
    return 0;
  }

  private void placeInactiveName(Text name) {
    var center = (Point2D) name.getProperties().get("center");
    var shape = (Path) name.getProperties().get("shape");
    var fitted = fit((String) name.getProperties().get("full"),
        chordAt(shape, center.getX(), center.getY()) * viewScale * NAME_SHARE / textZoom());
    name.setText(fitted != null ? fitted : "");
    name.getProperties().put("fits", fitted != null);
    centerAt(name, center.getX(), center.getY());
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
    editMarkers.toFront();
  }

  private void positionNameLabel(Territory territory) {
    var name = nameLabels.get(territory);
    if (name == null) {
      return;
    }
    var center = center(territory);
    var armies = getTerritoryNode(territory, Text.class).getLayoutBounds();
    // below the number of armies, which has a size on the screen that hardly depends on the zoom
    name.setText(territory.name());
    var nameHeight = name.getLayoutBounds().getHeight();
    double y = center.getY() + (armies.getHeight() / 2 + nameHeight / 2 + 1) * textZoom() / viewScale;
    // a name wider than the territory would cover its neighbours, so it is cut, with an ellipsis, or left out
    var path = getTerritoryNode(territory, Path.class);
    var fitted = fit(territory.name(), chordAt(path, center.getX(), y) * viewScale * NAME_SHARE / textZoom());
    name.setText(fitted != null ? fitted : "");
    name.getProperties().put("fits", fitted != null);
    centerAt(name, center.getX(), y);
    name.setVisible(showNames && fitted != null);
  }

  /** Where the labels of a territory are, and the connections to it end: where the map says, and otherwise the middle of its shape. */
  private Point2D center(Territory territory) {
    return defaultCenter(territory).add(labelOffsets.getOrDefault(territory, Point2D.ZERO));
  }

  /** Where the label of a territory is by default, before it is moved by hand. */
  private Point2D defaultCenter(Territory territory) {
    var base = territoryPathProvider.getLabelBase(territory.name());
    var label = base != null ? base : territoryPathProvider.getLabelPoint(territory.name());
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
    // the same width on the screen, whatever the zoom
    path.setStrokeWidth((markColor != null ? 4 : 1) / viewScale);
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
    centerAt(label, center.getX(), center.getY());
    positionNameLabel(territory);
  }
}
