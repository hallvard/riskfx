package no.hal.risk.fx;

import io.quarkiverse.fx.views.FxViewRepository;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.IntStream;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import no.hal.risk.FortifyMode;
import no.hal.risk.Player;
import no.hal.risk.Risk;
import no.hal.risk.RiskConfig;
import no.hal.risk.RiskListener;
import no.hal.risk.Territory;
import no.hal.risk.TurnState;
import org.jboss.logging.Logger;

/**
 * The first user interface, for human players only. It is replaced by {@link PlayApp}, which is shown at startup instead,
 * and is no longer loaded as a view. To use it again, add {@code @FxView} and {@code @Dependent} to this class,
 * and remove {@link PlayStartup}, since both would show a scene in the same stage.
 */
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

  @FXML
  Button attackAction;

  // 

  @FXML
  ComboBox<Territory> fromSelector;

  @FXML
  ComboBox<Territory> toSelector;

  @FXML
  ComboBox<Integer> fortifyNumArmiesSelector;

  //

  private RiskConfig riskConfig = new RiskConfig(4).withFortifyMode(FortifyMode.CONNECTED);
  private Risk risk;

  private WorldView worldView;

  // view state
  
  private TurnState.Reinforcement reinforcement;
  private TurnState.Attack attack;
  private TurnState.Fortification fortification;

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
      return FxColors.of(player.color());
    }
  };

  private LabelProvider<Territory> territoryLabelProvider = new LabelProvider<>() {
    @Override
    public String getLabel(Territory territory) {
      return "%s occupied by %s".formatted(territory.name(), territory.occupant().name());
    }

    @Override
    public Color getColor(Territory territory) {
      return FxColors.of(territory.occupant().color());
    }
  };

  @FXML
  void initialize() {
    risk = riskConfig.createRisk();
  
    LabelListCell.configure(playerSelector, playerLabelProvider);

    // reinforcement controls
    LabelListCell.configure(reinforceTerritorySelector, territoryLabelProvider);

    // attack controls
    LabelListCell.configure(attackerSelector, territoryLabelProvider);
    LabelListCell.configure(defenderSelector, territoryLabelProvider);

    // (re)fortification controls
    LabelListCell.configure(fromSelector, territoryLabelProvider);
    LabelListCell.configure(toSelector, territoryLabelProvider);

    worldView = new WorldView(mapPane, territoryPathProvider);
    worldView.worldProperty().setValue(risk.world());

    playerSelector.getItems().setAll(risk.players());

    risk.addRiskListener(riskListener);

    risk.start();

    attackerSelector.getSelectionModel().selectedItemProperty().subscribe(this::attackerSelected);
    defenderSelector.getSelectionModel().selectedItemProperty().subscribe(this::defenderSelected);

    fromSelector.getSelectionModel().selectedItemProperty().subscribe(this::fromSelected);
    toSelector.getSelectionModel().selectedItemProperty().subscribe(this::toSelected);

    worldView.setOnTerritoryReleased(release -> territoryClicked(release.territory()));

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

  private void updateReinforcementControls(TurnState.Reinforcing reinforcing) {
    if (reinforceTerritorySelector.getParent() instanceof Pane pane) {
      pane.getChildren().removeIf(node -> node instanceof Button);
      for (int i = 1; i <= reinforcing.totalArmies(); i++) {
        int numArmies = i;
        Button actionButton = new Button(String.valueOf(numArmies));
        actionButton.setOnAction(event -> reinforce(numArmies));
        pane.getChildren().add(actionButton);
      }
    }
  }

  void reinforce(int numArmies) {
    if (risk.currentTurnState() instanceof TurnState.Reinforcing reinforcing) {
      var reinforceTerritory = reinforceTerritorySelector.getValue();
      reinforcing.reinforce(risk, reinforceTerritory, numArmies);
      attackerSelector.getSelectionModel().select(reinforceTerritory);
    }
  }

  private void territoryUpdated(Territory territory) {
    worldView.updateTerritory(territory);
  }

  private void playerTurnStateUpdated(Player player, TurnState turnState) {
    playerSelector.getSelectionModel().select(player);
    var turnStateNum = switch (turnState) {
      case TurnState.Reinforcing _ -> 0;
      case TurnState.Attacking _ -> 1;
      case TurnState.Fortifying _ -> 2;
      case TurnState.GameOver _ -> 2;
    };
    turnStateTabPane.getSelectionModel()
        .select(turnStateTabPane.getTabs().get(turnStateNum));
    this.reinforcement = null;
    this.attack = null;
    this.fortification = null;
    turnStateUpdated(turnState);
  }

  // utility methods for ComboBox management

  private List<Integer> intItems(int from, int to) {
    return IntStream.rangeClosed(from, to).boxed().toList();
  }

  private <T> void clearItems(ComboBox<T> comboBox) {
    comboBox.getItems().clear();
  }

  private <T> void setItemsAndSelection(ComboBox<T> comboBox, List<T> items, T selectedItem, Integer defaultIndex) {
    if (defaultIndex != null && defaultIndex < 0) {
      defaultIndex = items.size() + defaultIndex;
    }
    comboBox.getItems().setAll(items);
    if (selectedItem != null && items.contains(selectedItem)) {
      logger.infof("Selecting item: %s", selectedItem);
      comboBox.getSelectionModel().select(selectedItem);
    } else if (defaultIndex != null && defaultIndex >= 0 && defaultIndex < items.size()) {
      selectedItem = items.get(defaultIndex);
      logger.infof("Selecting item: %s", selectedItem);
      comboBox.getSelectionModel().select(selectedItem);
    } else {
      logger.infof("Clearing selection");
      comboBox.getSelectionModel().clearSelection();
    }
  }
  private <T> void setItemsAndSelection(ComboBox<T> comboBox, List<T> items, T selectedItem) {
    setItemsAndSelection(comboBox, items, selectedItem, null);
  }

  // attacking

  // Each update method only touches the controls that depend on the one that changed,
  // never the combo box whose selection triggered the update.

  private void updateAttackControls() {
    logger.infof("Updating attack controls: %s", this.attack);
    setItemsAndSelection(attackerSelector, risk.playerTerritories(risk.currentPlayer()), this.attack.attacker());
    updateAttackerDependentControls();
  }

  private void updateAttackerDependentControls() {
    if (this.attack.attacker() != null) {
      setItemsAndSelection(attackerNumArmiesSelector, intItems(1, risk.maxAttackerDies(this.attack.attacker())), this.attack.numAttackerDies(), -1);
      setItemsAndSelection(defenderSelector, risk.connectedTerritories(this.attack.attacker(), false), this.attack.defender());
      updateDefenderDependentControls();
    } else {
      clearItems(attackerNumArmiesSelector);
      clearItems(defenderSelector);
      clearItems(defenderNumArmiesSelector);
    }
  }

  private void updateDefenderDependentControls() {
    if (this.attack.defender() != null) {
      setItemsAndSelection(defenderNumArmiesSelector, intItems(1, risk.maxDefenderDies(this.attack.defender())), this.attack.numDefenderDies(), -1);
    } else {
      clearItems(defenderNumArmiesSelector);
    }
  }

  private void attackerSelected(Territory territory) {
    logger.infof("Selected attacker %s", territory);
    this.attack = new TurnState.Attack(territory, null, -1, -1);
    updateAttackerDependentControls();
  }

  private void defenderSelected(Territory territory) {
    logger.infof("Selected defender %s", territory);
    this.attack = new TurnState.Attack(this.attack.attacker(), territory, -1, -1);
    updateDefenderDependentControls();
  }

  @FXML
  void attack() {
    if (risk.currentTurnState() instanceof TurnState.Attacking attacking) {
      var attackerTerritory = attackerSelector.getValue();
      var defenderTerritory = defenderSelector.getValue();
      var defenderDefeated = attacking.attack(risk,
          attackerTerritory,
          defenderTerritory,
          attackerNumArmiesSelector.getValue(),
          defenderNumArmiesSelector.getValue()
      );
      if (defenderDefeated) {
        attackerSelector.getSelectionModel().select(defenderTerritory);
      } else {
        // fake (re)selection of attacker, refreshing the labels of all controls
        this.attack = new TurnState.Attack(attackerTerritory, null, -1, -1);
        updateAttackControls();
      }
    }
  }

  @FXML
  void skipAttack() {
    if (risk.currentTurnState() instanceof TurnState.Attacking attacking) {
      risk.nextTurnState();
    }
  }

  // fortification

  private void updateFortificationControls() {
    logger.infof("Updating fortification controls: %s", this.fortification);
    setItemsAndSelection(fromSelector, risk.playerTerritories(risk.currentPlayer()), this.fortification.fromTerritory());
    updateFromDependentControls();
  }

  private void updateFromDependentControls() {
    if (this.fortification.fromTerritory() != null) {
      setItemsAndSelection(toSelector, risk.connectedTerritories(this.fortification.fromTerritory(), true), this.fortification.toTerritory());
      updateToDependentControls();
    } else {
      setItemsAndSelection(toSelector, risk.playerTerritories(risk.currentPlayer()), this.fortification.toTerritory());
      clearItems(fortifyNumArmiesSelector);
    }
  }

  private void updateToDependentControls() {
    if (this.fortification.fromTerritory() != null) {
      setItemsAndSelection(fortifyNumArmiesSelector, intItems(1, risk.maxFortifyNum(this.fortification.fromTerritory())), this.fortification.numArmies(), -1);
    } else {
      clearItems(fortifyNumArmiesSelector);
    }
  }

  private void fromSelected(Territory territory) {
    logger.infof("Selected from %s", territory);
    this.fortification = new TurnState.Fortification(territory, null, -1);
    updateFromDependentControls();
  }

  private void toSelected(Territory territory) {
    logger.infof("Selected to %s", territory);
    this.fortification = new TurnState.Fortification(this.fortification.fromTerritory(), territory, this.fortification.numArmies());
    updateToDependentControls();
  }

  @FXML
  void fortify() {
    if (risk.currentTurnState() instanceof TurnState.Fortifying fortifying) {
      fortifying.fortify(risk,
          fromSelector.getValue(),
          toSelector.getValue(),
          fortifyNumArmiesSelector.getValue()
      );
      risk.nextTurnState();
    }
  }

  @FXML
  void skipFortify() {
    if (risk.currentTurnState() instanceof TurnState.Fortifying fortifying) {
      risk.nextTurnState();
    }
  }
  
  private void territoryClicked(Territory territory) {
    logger.infof("Clicked on %s", territory.name());
    switch (risk.currentTurnState()) {
      case TurnState.Reinforcing _ -> {
        if (reinforceTerritorySelector.getItems().contains(territory)) {
          reinforceTerritorySelector.getSelectionModel().select(territory);
        }
      }
      case TurnState.Attacking _ -> {
        if (attackerSelector.getItems().contains(territory)) {
          attackerSelector.getSelectionModel().select(territory);
        } else if (defenderSelector.getItems().contains(territory)) {
          defenderSelector.getSelectionModel().select(territory);
        }
      }
      case TurnState.Fortifying _ -> {
        if (fromSelector.getValue() == territory) {
          fromSelector.getSelectionModel().clearSelection();
        } else if (fromSelector.getValue() == null && fromSelector.getItems().contains(territory)) {
          fromSelector.getSelectionModel().select(territory);
        } else if (toSelector.getItems().contains(territory)) {
          toSelector.getSelectionModel().select(territory);
        }
      }
      case TurnState.GameOver _ -> {
        // nothing to select
      }
    }
  }

  private void turnStateUpdated(TurnState turnState) {
    logger.infof("Turn state updated to %s", turnState);
    switch (turnState) {
      case TurnState.Reinforcing reinforcing -> {
        reinforceTerritorySelector.getItems().setAll(risk.playerTerritories(risk.currentPlayer()));
        updateReinforcementControls(reinforcing);
      }
      case TurnState.Attacking _ -> {
        if (this.attack == null) {
          this.attack = new TurnState.Attack(null, null, -1, -1);
        }
        updateAttackControls();
      }
      case TurnState.Fortifying _ -> {
        if (this.fortification == null) {
          this.fortification = new TurnState.Fortification(null, null, -1);
        }
        updateFortificationControls();
      }
      case TurnState.GameOver gameOver -> {
        logger.infof("Game over, %s won", gameOver.winner().name());
        List.of(reinforceTerritorySelector, attackerSelector, attackerNumArmiesSelector,
            defenderSelector, defenderNumArmiesSelector,
            fromSelector, toSelector, fortifyNumArmiesSelector
        ).forEach(comboBox -> comboBox.getItems().clear());
        fxViewRepository.getPrimaryStage().setTitle("RiskFx - %s won!".formatted(gameOver.winner().name()));
      }
    }
  }
}
