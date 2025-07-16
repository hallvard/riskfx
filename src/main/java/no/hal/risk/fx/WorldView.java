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
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
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
    mapPane.setOnMouseClicked(event -> {
      Node node = event.getPickResult().getIntersectedNode();
      if (node != null && node.getUserData() instanceof Territory territory) {
        if (onTerritoryClicked != null) {
          Platform.runLater(() -> onTerritoryClicked.accept(territory));
        }
      }
    });
  }

  private Property<World> worldProperty = new SimpleObjectProperty<>();

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

  private Consumer<Territory> onTerritoryClicked = null;

  public void setOnTerritoryClicked(Consumer<Territory> onTerritoryClicked) {
    this.onTerritoryClicked = onTerritoryClicked;
  }

  private void addTerritoryNodes(Territory territory, Node... nodes) {
    territoryNodes.computeIfAbsent(territory, t -> new ArrayList<>()).addAll(List.of(nodes));
  }

  private void updateMap() {
    territoryNodes.clear();
    var children = mapPane.getChildren();
    children.clear();
    // add paths
    children.addAll(getWorld().allTerritories().stream()
        .map(territory -> {
          var path = territoryPathProvider.getPath(territory.name());
          addTerritoryNodes(territory, path);
          updateTerritoryPath(territory);
          path.setUserData(territory);
          return path;
        })
        .toList()
    );
    // add labels
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
  }

  private <T extends Node> T getTerritoryNode(Territory territory, Class<T> nodeType) {
    return territoryNodes.get(territory).stream()
        .filter(nodeType::isInstance)
        .map(nodeType::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No node of type " + nodeType.getSimpleName() + " for territory: " + territory.name()));
  }

  public void updateTerritory(Territory territory) {
    updateTerritoryPath(territory);
    updateTerritoryLabel(territory);
  }

  private void updateTerritoryPath(Territory territory) {
    var path = getTerritoryNode(territory, Path.class);
    Color fillColor = territory.occupant().color();
    path.setFill(fillColor);
    path.setStroke(Color.BLACK);
  }

  private void updateTerritoryLabel(Territory territory) {
    var label = getTerritoryNode(territory, Text.class);
    String labelString = territory.name();
    if (territory.numArmies() > 0) {
      labelString = String.valueOf(territory.numArmies());
    }
    label.setText(labelString);
    var path = getTerritoryNode(territory, Path.class);
    var pathBounds = path.getBoundsInLocal();
    var nameBounds = label.getLayoutBounds();
    label.setLayoutX(pathBounds.getCenterX() - nameBounds.getWidth() / 2);
    label.setLayoutY(pathBounds.getCenterY() - nameBounds.getHeight() / 2);
  }
}
