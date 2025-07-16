package no.hal.risk.fx;

import io.quarkiverse.fx.views.FxView;
import io.quarkiverse.fx.views.FxViewRepository;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import no.hal.risk.Player;
import no.hal.risk.Risk;
import no.hal.risk.RiskConfig;
import no.hal.risk.RiskListener;
import no.hal.risk.Territory;
import no.hal.risk.TurnState;
import org.jboss.logging.Logger;

@FxView
@Dependent
public class RiskController {

  @Inject
  Logger logger;

  @Inject
  TerritoryPathProvider territoryPathProvider;

  @Inject
  FxViewRepository fxViewRepository;

  @FXML
  ComboBox<Player> playerSelector;

  @FXML
  Pane riskPane;

  @FXML
  Pane mapPane;

  @FXML
  TabPane turnStateTabPane;

  // Reinforce tab

  @FXML
  ComboBox<Territory> reinforceTerritorySelector;
  
  @FXML
  Button reinforce1Action;

  // Attack tab

  @FXML
  ComboBox<Territory> attackerSelector;
  
  @FXML
  ComboBox<Integer> attackerNumArmiesSelector;

  @FXML
  ComboBox<Territory> defenderSelector;
  
  @FXML
  ComboBox<Integer> defenderNumArmiesSelector;

  // 

  @FXML
  ComboBox<Territory> fromSelector;

  @FXML
  ComboBox<Territory> toSelector;

  @FXML
  ComboBox<Integer> fortifyNumArmiesSelector;

  private RiskConfig riskConfig = new RiskConfig(4);
  private Risk risk;

  private WorldView worldView;

  private RiskListener riskListener = new RiskListener() {
      @Override
      public void territoryUpdated(Risk risk, Territory territory) {
        RiskController.this.territoryUpdated(territory);
      }

      @Override
      public void playerTurnStateUpdated(Risk risk, Player player, TurnState turnState) {
        RiskController.this.playerTurnStateUpdated(player, turnState);
      }
  };

  private LabelProvider<Player> playerLabelProvider = new LabelProvider<>() {
    @Override
    public String getLabel(Player player) {
      return player.name();
    }
    @Override
    public Color getColor(Player player) {
      return player.color();
    }
  };

  private LabelProvider<Territory> territoryLabelProvider = new LabelProvider<>() {
    @Override
    public String getLabel(Territory territory) {
      return "%s occupied by %s".formatted(territory.name(), territory.occupant().name());
    }

    @Override
    public Color getColor(Territory territory) {
      return territory.occupant().color();
    }
  };

  @FXML
  void initialize() {
    risk = riskConfig.createRisk();
  
    playerSelector.setButtonCell(new LabelListCell<>(playerLabelProvider));
    playerSelector.setCellFactory(new LabelListCell.Factory<>(playerLabelProvider));
    // reinforcement controls
    reinforceTerritorySelector.setButtonCell(new LabelListCell<>(territoryLabelProvider));
    reinforceTerritorySelector.setCellFactory(new LabelListCell.Factory<>(territoryLabelProvider));
    // attack controls
    attackerSelector.setButtonCell(new LabelListCell<>(territoryLabelProvider));
    attackerSelector.setCellFactory(new LabelListCell.Factory<>(territoryLabelProvider));
    defenderSelector.setButtonCell(new LabelListCell<>(territoryLabelProvider));
    defenderSelector.setCellFactory(new LabelListCell.Factory<>(territoryLabelProvider));
    // refortification controls
    fromSelector.setButtonCell(new LabelListCell<>(territoryLabelProvider));
    fromSelector.setCellFactory(new LabelListCell.Factory<>(territoryLabelProvider));
    toSelector.setButtonCell(new LabelListCell<>(territoryLabelProvider));
    toSelector.setCellFactory(new LabelListCell.Factory<>(territoryLabelProvider));

    worldView = new WorldView(mapPane, territoryPathProvider);
    worldView.worldProperty().setValue(risk.world());

    playerSelector.getItems().setAll(risk.players());

    risk.addRiskListener(riskListener);

    risk.start();
    worldView.setOnTerritoryClicked(this::territoryClicked);

    
    var scene = new Scene(riskPane, 1000, 800);
    var stage = fxViewRepository.getPrimaryStage();
    stage.setOnCloseRequest(_ -> {
        Platform.exit();
        System.exit(0);
    });
    stage.setScene(scene);
    stage.setTitle("RiskFx");
    stage.show();
  }

  private void updateReinforceActions(TurnState.Reinforcing reinforcing) {
    if (reinforceTerritorySelector.getParent() instanceof Pane pane) {
      pane.getChildren().removeIf(node -> node instanceof Button);
      for (int i = 1; i <= reinforcing.totalArmies(); i++) {
        int numArmies = i;
        Button actionButton = new Button(String.valueOf(numArmies));
        actionButton.setOnAction(event -> reinforcing.reinforce(risk, reinforceTerritorySelector.getValue(), numArmies));
        pane.getChildren().add(actionButton);
      }
    }
  }

  private void territoryUpdated(Territory territory) {
    worldView.updateTerritory(territory);
  }

  private void playerTurnStateUpdated(Player player, TurnState turnState) {
    playerSelector.getSelectionModel().select(player);
    var turnStateNum = switch (turnState) {
      case TurnState.Reinforcing reinforcing -> 0;
      case TurnState.Attacking attacking -> 1;
      case TurnState.Fortifying fortifying -> 2;
    };
    turnStateTabPane.getSelectionModel()
        .select(turnStateTabPane.getTabs().get(turnStateNum));
    turnStateUpdated(turnState);
  }

  private void territoryClicked(Territory territory) {
    logger.infof("Clicked on %s", territory.name());
    switch (risk.currentTurnState()) {
      case TurnState.Reinforcing reinforcing -> {
        if (reinforceTerritorySelector.getItems().contains(territory)) {
          reinforceTerritorySelector.getSelectionModel().select(territory);
        }
      }
      case TurnState.Attacking attacking -> {
        if (attackerSelector.getItems().contains(territory)) {
          attackerSelector.getSelectionModel().select(territory);
        } else if (defenderSelector.getItems().contains(territory)) {
          defenderSelector.getSelectionModel().select(territory);
        }
      }
      case TurnState.Fortifying fortifying -> {
        if (fromSelector.getItems().contains(territory)) {
          fromSelector.getSelectionModel().select(territory);
        } else if (toSelector.getItems().contains(territory)) {
          toSelector.getSelectionModel().select(territory);
        }
      }
    }
  }

  private void turnStateUpdated(TurnState turnState) {
    switch (turnState) {
      case TurnState.Reinforcing reinforcing -> {
        reinforceTerritorySelector.getItems().setAll(risk.playerTerritories(risk.currentPlayer(), null));
        updateReinforceActions(reinforcing);
      }
      case TurnState.Attacking attacking -> {
        attackerSelector.getItems().setAll(risk.playerTerritories(risk.currentPlayer(), null));
        attackerSelector.getSelectionModel().clearSelection();
        attackerNumArmiesSelector.getSelectionModel().clearSelection();
        defenderSelector.getSelectionModel().clearSelection();
        defenderNumArmiesSelector.getSelectionModel().clearSelection();
      }
      case TurnState.Fortifying fortifying -> {
        fromSelector.getSelectionModel().clearSelection();
        toSelector.getSelectionModel().clearSelection();
        fortifyNumArmiesSelector.getSelectionModel().clearSelection();
      }
    }
  }
}
