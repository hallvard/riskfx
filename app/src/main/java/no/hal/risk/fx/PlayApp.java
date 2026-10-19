package no.hal.risk.fx;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import no.hal.risk.FortifyMode;
import no.hal.risk.GameMap;
import no.hal.risk.Player;
import no.hal.risk.Risk;
import no.hal.risk.RiskConfig;
import no.hal.risk.RiskListener;
import no.hal.risk.Rules;
import no.hal.risk.Territory;
import no.hal.risk.TurnState;
import no.hal.risk.ai.Bot;
import no.hal.risk.ai.BotMatch;
import no.hal.risk.ai.CautiousBot;
import no.hal.risk.ai.GreedyBot;
import no.hal.risk.ai.HumanBot;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;

/**
 * Play and watch in the same window. At the top you choose the number of players, or a ready-made combination,
 * and what controls each player: a human, a greedy bot or the trained AI. The bots play by themselves, at the chosen speed.
 * After each move, the territories involved are outlined, after an attack by how many armies each lost.
 *
 * <p>The trained networks are in the application. The options {@code ai2} and {@code ai4} (the arguments {@code --ai2=file}
 * and {@code --ai4=file}) can say where other ones are, for games of two players and of three or four.
 */
public class PlayApp extends Application {

  private static final int MAX_TURNS = 400;
  private static final String DEFAULT_PRESET = "Human against AI";

  private AiNetworks aiNetworks;
  private BotMatch match;
  private HumanTurn humanTurn;
  private MapViewport mapViewport;
  private ComboBox<GameMap> mapBox;
  private CheckBox showNamesBox;
  private CheckBox showRegionsBox;
  private CheckBox editLabelsBox;
  private GameMap currentMap = GameMap.CLASSIC;
  private Map<String, Point2D> labelOffsetsAtStart = Map.of();
  private CheckBox keepArmiesBackBox;
  private CheckBox conquestBonusBox;
  private CheckBox eliminationBonusBox;
  private final NumberMenuController numberMenu = new NumberMenuController();
  private WorldView worldView;
  private GameSetup setup;
  private long nextSeed = System.nanoTime() % 100_000;
  /** The outlines of the last move. */
  private Map<Territory, Color> lastMoveOutlines = Map.of();
  /** The last move of a human, for adding the attack of a click to those of the double click that follows. */
  private BotMatch.Move lastHumanMove;
  /** A message about why the last click did nothing. */
  private String hint = "";

  // setup controls
  private ComboBox<GameSetup.Preset> presetBox;
  private final List<ComboBox<PlayerKind>> kindBoxes = new ArrayList<>();
  private final List<HBox> kindRows = new ArrayList<>();
  // game controls
  private Label statusLabel;
  private VBox playersBox;
  private ToggleButton playButton;
  private Button stepButton;
  private Slider speedSlider;
  private PauseTransition timer;
  // controls of a human player
  private VBox humanBar;
  private Label instructionLabel;

  private RiskEnv env() {
    return match.env();
  }

  BotMatch match() {
    return match;
  }

  /** For the tool that renders the interface. */
  MapViewport mapViewport() {
    return mapViewport;
  }

  /** For the tool that renders the interface. */
  WorldView worldView() {
    return worldView;
  }

  HumanTurn humanTurn() {
    return humanTurn;
  }

  /** Whether the outlines on the map are from an attack. For the tool that renders the interface. */
  boolean lastMoveWasAttack() {
    return lastMoveOutlines.values().stream().anyMatch(color -> color != BattleColors.OTHER_MOVE);
  }

  void aiFiles(Path twoPlayers, Path moreThanTwoPlayers) {
    aiNetworks = new AiNetworks(twoPlayers, moreThanTwoPlayers);
  }

  /** Used when started as a JavaFX application, by {@link PlayLauncher}. */
  @Override
  public void start(Stage stage) {
    show(stage, getParameters().getNamed());
  }

  /**
   * Shows the window in a stage and starts a game.
   *
   * @param options {@code ai2} and {@code ai4} are files with trained networks, for games of two players and of three or four,
   *   which are those in the application if they are not given
   */
  public void show(Stage stage, Map<String, String> options) {
    // PyTorch scales badly beyond a few threads for a network of this size
    System.setProperty("ai.djl.pytorch.num_threads", System.getProperty("ai.djl.pytorch.num_threads", "4"));
    aiNetworks = new AiNetworks(options.containsKey("ai2") ? Path.of(options.get("ai2")) : null,
        options.containsKey("ai4") ? Path.of(options.get("ai4")) : null);
    stage.setScene(new Scene(createContent(), 1100, 860));
    stage.setTitle("RiskFx");
    stage.setOnCloseRequest(_ -> {
      timer.stop();
      Platform.exit();
      System.exit(0);
    });
    stage.show();
    startNewGame();
  }

  /** The user interface, without a game, which is started with {@link #startNewGame()}. */
  Parent createContent() {
    if (aiNetworks == null) {
      aiNetworks = new AiNetworks();
    }
    var mapPane = new Pane();
    worldView = new WorldView(mapPane, new TerritoryPathProvider());
    worldView.setOnTerritoryReleased(this::territoryReleased);

    timer = new PauseTransition(Duration.millis(200));
    timer.setOnFinished(_ -> {
      botMove();
    });

    var root = new BorderPane();
    root.setTop(createTop());
    mapViewport = new MapViewport(mapPane);
    root.setCenter(mapViewport);
    // the text and lines of the map keep their size on the screen when zooming, which gives room for more of the names
    mapViewport.scaleProperty().subscribe(scale -> worldView.setViewScale(scale.doubleValue()));
    root.setBottom(createHumanBar());
    // Enter ends the attack or skips the fortification, which is also in the menus, and 0 fits the map to the window again
    root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.DIGIT0 && ! event.isShortcutDown()) {
        mapViewport.reset();
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.ENTER && match != null && match.isHumanTurn()) {
        var action = switch (env().phase()) {
          case ATTACKING -> new Action.EndAttack();
          case FORTIFYING -> new Action.SkipFortify();
          default -> null;
        };
        if (action != null) {
          numberMenu.hide();
          humanAction(action);
          event.consume();
        }
      }
    });
    return root;
  }

  private Parent createTop() {
    // what to play: the number of players or a combination, and what controls each player
    presetBox = new ComboBox<>();
    presetBox.getItems().setAll(GameSetup.presets());
    presetBox.setPrefWidth(300);
    var kindsBox = new HBox(14);
    kindsBox.setAlignment(Pos.CENTER_LEFT);
    for (int i = 0; i < GameSetup.MAX_PLAYERS; i++) {
      var swatch = new Rectangle(14, 14, FxColors.of(RiskConfig.playerColor(i)));
      swatch.setStroke(Color.BLACK);
      var kindBox = new ComboBox<PlayerKind>();
      kindBox.getItems().setAll(PlayerKind.values());
      kindBoxes.add(kindBox);
      var row = new HBox(4, swatch, new Label("Player " + (i + 1)), kindBox);
      row.setAlignment(Pos.CENTER_LEFT);
      kindRows.add(row);
      kindsBox.getChildren().add(row);
    }
    presetBox.valueProperty().subscribe(preset -> {
      if (preset != null) {
        applyPreset(preset.setup());
      }
    });
    var newGameButton = new Button("New game");
    newGameButton.setDefaultButton(false);
    newGameButton.setOnAction(_ -> startNewGame());
    showNamesBox = new CheckBox("Show territory names");
    showNamesBox.setSelected(true);
    showNamesBox.selectedProperty().subscribe(show -> worldView.setShowNames(show));
    showRegionsBox = new CheckBox("Show regions");
    showRegionsBox.selectedProperty().subscribe(show -> worldView.setShowRegions(show));
    editLabelsBox = new CheckBox("Edit label places");
    editLabelsBox.setTooltip(new Tooltip("Drag the number and name of a territory to move them, and double-click to put them back. "
        + "When switched off, the places can be saved for the map generator. Only for maps that have a file for it."));
    editLabelsBox.selectedProperty().addListener((_, _, editing) -> editLabelsChanged(editing));
    mapBox = new ComboBox<>();
    mapBox.getItems().setAll(GameMap.all());
    mapBox.setValue(GameMap.CLASSIC);
    keepArmiesBackBox = new CheckBox("Keep armies back");
    keepArmiesBackBox.setTooltip(new Tooltip("After a conquest, move in as many armies as you choose, but at least as many as dice were rolled. Bots move in all they can."));
    eliminationBonusBox = new CheckBox("Bonus for eliminating a player");
    eliminationBonusBox.setTooltip(new Tooltip("%d more armies the next turn for each player you eliminated.".formatted(Risk.ELIMINATION_BONUS)));
    conquestBonusBox = new CheckBox("Bonus after conquest");
    conquestBonusBox.setTooltip(new Tooltip("A turn gives %d armies and the continent bonuses, and %d more if a territory was conquered the turn before.".formatted(Risk.ALWAYS_REINFORCEMENT, Risk.CONQUEST_BONUS)));
    speedSlider = new Slider(0, 100, 70);
    speedSlider.setPrefWidth(160);
    var settingsButton = createSettingsButton();
    var setupSpacer = new Region();
    HBox.setHgrow(setupSpacer, Priority.ALWAYS);
    var setupRow = new HBox(10, new Label("Players:"), presetBox, newGameButton, setupSpacer, settingsButton);
    setupRow.setAlignment(Pos.CENTER_LEFT);

    // status and the speed of the bots
    statusLabel = new Label();
    statusLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold");
    playButton = new ToggleButton("Pause");
    playButton.setSelected(true);
    playButton.selectedProperty().subscribe(playing -> {
      playButton.setText(playing ? "Pause" : "Play");
      if (playing) {
        scheduleBotMove();
      } else {
        timer.stop();
      }
      updateControls();
    });
    stepButton = new Button("Step");
    stepButton.setOnAction(_ -> botMove());
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var statusRow = new HBox(10, statusLabel, spacer, new Label("Bot speed"), speedSlider, playButton, stepButton);
    statusRow.setAlignment(Pos.CENTER_LEFT);

    playersBox = new VBox(2);
    var top = new VBox(6, setupRow, kindsBox, statusRow, playersBox);
    top.setPadding(new Insets(8));
    return top;
  }

  /** The cog button in the upper right corner, which shows the settings in a popup that closes when clicking outside it. */
  private Button createSettingsButton() {
    var button = new Button("\u2699");
    button.setStyle("-fx-font-size: 16;");
    button.setTooltip(new Tooltip("Settings"));
    var zoomNote = new Label("Zoom with Ctrl or Cmd and scroll, or pinch. Move with scroll, or drag with the right button. Press 0 to fit.");
    zoomNote.setWrapText(true);
    zoomNote.setMaxWidth(260);
    zoomNote.setStyle("-fx-text-fill: gray;");
    var note = new Label("The map and the rules are read when a new game starts.");
    note.setStyle("-fx-text-fill: gray;");
    var content = new VBox(8, new Label("View"), showNamesBox, showRegionsBox, editLabelsBox, zoomNote, new Label("Map"), mapBox, new Label("Rules"), keepArmiesBackBox, conquestBonusBox, eliminationBonusBox, note);
    content.setPadding(new Insets(12));
    content.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 4; -fx-border-radius: 4;");
    var popup = new Popup();
    popup.setAutoHide(true);
    popup.getContent().add(content);
    button.setOnAction(_ -> {
      if (popup.isShowing()) {
        popup.hide();
      } else {
        var bounds = button.localToScreen(button.getBoundsInLocal());
        // below the button, with the right edges aligned
        popup.show(button, bounds.getMaxX() - 230, bounds.getMaxY() + 4);
      }
    });
    return button;
  }

  /** Editing the places of the labels is switched on, and then off, which asks whether to save what was moved. */
  private void editLabelsChanged(boolean editing) {
    if (editing) {
      labelOffsetsAtStart = worldView.labelOffsets();
      worldView.setEditLabels(true);
      updateControls();
      return;
    }
    worldView.setEditLabels(false);
    var offsets = worldView.labelOffsets();
    if (! offsets.equals(labelOffsetsAtStart)) {
      if (! askToSaveLabelOffsets(offsets)) {
        worldView.setLabelOffsets(labelOffsetsAtStart);
      }
    }
    updateControls();
  }

  /** Asks whether to save the displaced labels, and does if so. Returns whether they are kept (saved). */
  private boolean askToSaveLabelOffsets(Map<String, Point2D> offsets) {
    var file = findLabelOffsetsFile(currentMap.labelOffsetsFile());
    var save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
    var discard = new ButtonType("Discard", ButtonBar.ButtonData.CANCEL_CLOSE);
    var alert = new Alert(Alert.AlertType.CONFIRMATION,
        "%d labels are away from where the map has them.\nSave their places in %s?\nRun the generator of the map afterwards to use them.".formatted(
            offsets.size(), file != null ? file : "a file that you choose"), save, discard);
    alert.setHeaderText("Save the places of the labels?");
    if (alert.showAndWait().orElse(discard) != save) {
      return false;
    }
    if (file == null) {
      var chooser = new FileChooser();
      chooser.setTitle("Save the places of the labels");
      chooser.setInitialFileName(Path.of(currentMap.labelOffsetsFile()).getFileName().toString());
      var chosen = chooser.showSaveDialog(worldView.node().getScene().getWindow());
      if (chosen == null) {
        return false;
      }
      file = chosen.toPath();
    }
    try {
      var displacements = new java.util.TreeMap<String, double[]>();
      offsets.forEach((name, offset) -> displacements.put(name, new double[] {Math.round(offset.getX() * 10) / 10.0, Math.round(offset.getY() * 10) / 10.0}));
      new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), displacements);
      hint = "Saved the places of %d labels in %s.".formatted(offsets.size(), file);
      return true;
    } catch (IOException e) {
      new Alert(Alert.AlertType.ERROR, "Could not save: " + e.getMessage()).showAndWait();
      return false;
    }
  }

  /** The file with the given path from the root of the project, found by looking in the directory where the application runs and above it. */
  static Path findLabelOffsetsFile(String relativePath) {
    var directory = Path.of("").toAbsolutePath();
    for (int levels = 0; directory != null && levels < 5; levels++, directory = directory.getParent()) {
      var candidate = directory.resolve(relativePath);
      if (java.nio.file.Files.isDirectory(candidate.getParent())) {
        return candidate;
      }
    }
    return null;
  }

  private Parent createHumanBar() {
    instructionLabel = new Label();
    instructionLabel.setWrapText(true);
    instructionLabel.setMinHeight(36);
    humanBar = new VBox(6, instructionLabel);
    humanBar.setPadding(new Insets(8));
    return humanBar;
  }

  // setup

  private void applyPreset(GameSetup preset) {
    for (int i = 0; i < kindBoxes.size(); i++) {
      boolean used = i < preset.playerCount();
      kindRows.get(i).setVisible(used);
      kindRows.get(i).setManaged(used);
      if (used) {
        kindBoxes.get(i).setValue(preset.kinds().get(i));
      }
    }
  }

  /** Shows a setup in the controls: the matching ready-made choice, if there is one, and the kind of each player. */
  private void showSetup(GameSetup shown) {
    presetBox.getItems().stream().filter(preset -> preset.setup().equals(shown)).findFirst().ifPresent(presetBox::setValue);
    if (presetBox.getValue() == null || presetBox.getValue().setup().playerCount() != shown.playerCount()) {
      presetBox.getItems().stream().filter(preset -> preset.setup().playerCount() == shown.playerCount()).findFirst().ifPresent(presetBox::setValue);
    }
    applyPreset(shown);
  }

  /** The setup as chosen in the controls. */
  private GameSetup chosenSetup() {
    int count = presetBox.getValue().setup().playerCount();
    return new GameSetup(kindBoxes.subList(0, count).stream().map(ComboBox::getValue).toList());
  }

  /** Starts a game with the players as chosen in the controls. */
  void startNewGame() {
    if (presetBox.getValue() == null) {
      presetBox.getItems().stream().filter(preset -> preset.name().equals(DEFAULT_PRESET)).findFirst().ifPresent(presetBox::setValue);
    }
    var chosen = chosenSetup();
    var rules = new Rules(keepArmiesBackBox.isSelected(), conquestBonusBox.isSelected(), eliminationBonusBox.isSelected());
    startGame(chosen, chosen.fortifyMode(), rules, mapBox.getValue(), nextSeed++);
  }

  void startGame(GameSetup newSetup, FortifyMode fortifyMode, long seed) {
    startGame(newSetup, fortifyMode, Rules.DEFAULT, seed);
  }

  void startGame(GameSetup newSetup, FortifyMode fortifyMode, Rules rules, long seed) {
    startGame(newSetup, fortifyMode, rules, GameMap.CLASSIC, seed);
  }

  void startGame(GameSetup newSetup, FortifyMode fortifyMode, Rules rules, GameMap map, long seed) {
    timer.stop();
    // a new map has its own labels, so editing the labels of the last one ends, with the question whether to save
    editLabelsBox.setSelected(false);
    currentMap = map;
    editLabelsBox.setDisable(map.labelOffsetsFile() == null);
    var bots = new ArrayList<Bot>();
    for (var kind : newSetup.kinds()) {
      switch (kind) {
        case HUMAN -> bots.add(new HumanBot());
        case GREEDY -> bots.add(new GreedyBot());
        case CAUTIOUS -> bots.add(new CautiousBot());
        case AI -> {
          try {
            bots.add(aiNetworks.botFor(newSetup.playerCount(), map));
          } catch (IOException e) {
            statusLabel.setText("No AI network for %d players: could not read %s (%s)".formatted(
                newSetup.playerCount(), aiNetworks.describe(newSetup.playerCount()), e.getMessage()));
            scheduleBotMove();
            return;
          }
        }
      }
    }
    setup = newSetup;
    showSetup(newSetup);
    var env = new RiskEnv(newSetup.riskConfig(fortifyMode, rules, map), MAX_TURNS);
    match = new BotMatch(env, bots);
    humanTurn = new HumanTurn(env);
    lastMoveOutlines = Map.of();
    hint = "";
    // the map is built for the new game, and then updated when the board is dealt
    worldView.setTerritoryPathProvider(new TerritoryPathProvider(map.shapesResource(), map.labelOffsetsResource()));
    worldView.worldProperty().setValue(env.risk().world());
    worldView.setMarkedTerritories(Map.of());
    env.risk().addRiskListener(new RiskListener() {
      @Override
      public void territoryUpdated(Risk risk, Territory territory) {
        worldView.updateTerritory(territory);
      }

      @Override
      public void playerTurnStateUpdated(Risk risk, Player player, TurnState turnState) {
      }
    });
    match.reset(seed, bots);
    playButton.setSelected(true);
    render();
    scheduleBotMove();
  }

  // moves

  /** The mouse button was released on a territory: a short press does the default action, and a long press shows a menu of the variants. */
  void territoryReleased(WorldView.TerritoryRelease release) {
    if (release.held()) {
      territoryMenu(release.territory(), release.screenX(), release.screenY());
    } else {
      territoryDefault(release.territory());
    }
  }

  /**
   * The default action of a territory, which is what a quick press does: in the first menu entry of a long press.
   * Reinforcing places all the armies that are left, attacking attacks once and, if the territory is conquered, moves in all the armies that can be moved, and fortifying moves as many armies as possible.
   * On your own territory when attacking, or when fortifying and the target hasn't been chosen, it selects the territory.
   */
  void territoryDefault(Territory territory) {
    if (match == null || ! match.isHumanTurn()) {
      return;
    }
    numberMenu.hide();
    switch (env().phase()) {
      case REINFORCING -> {
        var actions = humanTurn.reinforceAll(territory);
        if (actions.isEmpty()) {
          select(territory);
        } else {
          placeArmies(territory, actions.size());
        }
      }
      case ATTACKING -> {
        if (territory.occupant() == env().currentPlayer() || humanTurn.selectedFrom() == null) {
          select(territory);
        } else {
          attackOnce(territory, false, 0, 0);
        }
      }
      case FORTIFYING -> {
        select(territory);
        var action = humanTurn.fortifyMax(territory);
        var move = action != null ? performHuman(action) : null;
        if (move != null) {
          lastMoveOutlines = MoveHighlight.of(move, env());
        }
      }
      case GAME_OVER -> {
      }
    }
    finishHumanAction();
  }

  /** Changes the selection, which also shows why a territory can't be selected, and shows that instead of the last move. */
  private void select(Territory territory) {
    var click = humanTurn.click(territory);
    hint = click.message() != null ? click.message() : "";
    // a click that only changes the selection, so show that instead of the last move
    if (click.action() == null) {
      lastMoveOutlines = Map.of();
      lastHumanMove = null;
    }
  }

  /** A long press: a menu of what to do with the territory, with the default first and under the mouse. */
  void territoryMenu(Territory territory, double x, double y) {
    if (match == null || ! match.isHumanTurn()) {
      return;
    }
    numberMenu.hide();
    var owner = worldView.node();
    switch (env().phase()) {
      case REINFORCING -> {
        int left = humanTurn.reinforceAll(territory).size();
        if (left == 0) {
          territoryDefault(territory);
        } else {
          numberMenu.show(owner, x, y, 1, left, left,
              n -> n == left ? "Place all %d".formatted(n) : "Place %d".formatted(n),
              n -> {
                placeArmies(territory, n);
                finishHumanAction();
              });
        }
      }
      case ATTACKING -> {
        var endAttack = new NumberMenuController.Choice("End attack", () -> humanAction(new Action.EndAttack()));
        boolean attack = territory.occupant() != env().currentPlayer() && humanTurn.selectedFrom() != null;
        // the menu for the armies to move in after a conquest is only shown if the rule lets the player choose
        var first = attack
            ? new NumberMenuController.Choice("Attack here", () -> attackOnce(territory, true, x, y))
            : new NumberMenuController.Choice(territory == humanTurn.selectedFrom() ? "Deselect" : "Attack from here",
                () -> territoryDefault(territory));
        // ending the attack is the default of the menu, since a quick press already attacks
        numberMenu.show(owner, x, y, List.of(endAttack, first));
      }
      case FORTIFYING -> {
        var skip = new NumberMenuController.Choice("Skip fortify", () -> humanAction(new Action.SkipFortify()));
        if (humanTurn.canFortifyTo(territory)) {
          select(territory);
          render();
          int max = humanTurn.maxFortifyArmies();
          // skipping is the default, since a quick press already moves as many as possible
          var choices = new java.util.ArrayList<NumberMenuController.Choice>();
          choices.add(skip);
          choices.addAll(NumberMenuController.numberChoices(1, max, max,
              n -> n == max ? "Move all (%d)".formatted(n) : "Move %d".formatted(n),
              n -> humanAction(humanTurn.fortify(n))));
          numberMenu.show(owner, x, y, choices);
        } else {
          var first = new NumberMenuController.Choice(territory == humanTurn.selectedFrom() ? "Deselect" : "Move from here",
              () -> territoryDefault(territory));
          numberMenu.show(owner, x, y, List.of(skip, first));
        }
      }
      case GAME_OVER -> {
      }
    }
  }

  /** Places armies on the territory, one at a time like bots do, and outlines the last one. */
  private void placeArmies(Territory territory, int count) {
    BotMatch.Move last = null;
    for (int i = 0; i < count && ! humanTurn.reinforceAll(territory).isEmpty(); i++) {
      var move = performHuman(humanTurn.reinforceAll(territory).get(0));
      if (move == null) {
        break;
      }
      last = move;
    }
    if (last != null) {
      lastMoveOutlines = MoveHighlight.of(last, env());
    }
  }

  /** After a conquest, a menu of how many armies to have in the conquered territory, from the number that had to move in. */
  private void showOccupyMenu(Risk.Occupation occupation, double x, double y) {
    int min = occupation.to().numArmies();
    int max = min + occupation.maxExtra();
    numberMenu.show(worldView.node(), x, y, min, max, max,
        n -> n == max ? "Move in all (%d)".formatted(n) : "Move in %d".formatted(n),
        n -> {
          if (match != null && match.isHumanTurn() && env().risk().pendingOccupation() == occupation) {
            env().occupy(n - min);
            hint = "";
            render();
          }
        });
  }

  private void finishHumanAction() {
    skipForcedPhases();
    render();
    scheduleBotMove();
  }

  /** Performs an action chosen by the human, which has the same effect as an action chosen by a bot. Null if it was not allowed. */
  private BotMatch.Move performHuman(Action action) {
    BotMatch.Move move;
    try {
      move = match.perform(action);
    } catch (IllegalArgumentException e) {
      hint = e.getMessage();
      return null;
    }
    hint = "";
    humanTurn.afterAction(action);
    return move;
  }

  void humanAction(Action action) {
    if (action == null || ! match.isHumanTurn()) {
      return;
    }
    var move = performHuman(action);
    lastHumanMove = move;
    if (move != null) {
      lastMoveOutlines = MoveHighlight.of(move, env());
    }
    skipForcedPhases();
    render();
    scheduleBotMove();
  }

  /** Skips the phases with nothing to do, and tells a human which, since they would otherwise just seem to vanish. */
  private void skipForcedPhases() {
    var skipped = match.skipForcedPhases().stream()
        .filter(move -> move.bot() instanceof HumanBot)
        .map(move -> move.action() instanceof Action.EndAttack ? "the attack, since no territory can attack" : "the fortification, since no armies can be moved")
        .toList();
    if (! skipped.isEmpty()) {
      hint = "Skipped " + String.join(" and ", skipped) + ".";
    }
  }

  /**
   * Attacks the territory once from the selected attacker, and outlines the losses.
   * After a conquest with the rule to keep armies back, a menu of how many armies to move in is shown if {@code chooseOccupation},
   * and otherwise all of them are moved in.
   */
  private void attackOnce(Territory defender, boolean chooseOccupation, double x, double y) {
    var click = humanTurn.click(defender);
    hint = click.message() != null ? click.message() : "";
    if (click.action() == null) {
      return;
    }
    var move = performHuman(click.action());
    if (move != null) {
      lastHumanMove = move;
      lastMoveOutlines = MoveHighlight.of(move, env());
    }
    if (! chooseOccupation) {
      env().occupyAll();
    }
    finishHumanAction();
    if (chooseOccupation && match.isHumanTurn() && env().risk().pendingOccupation() != null) {
      showOccupyMenu(env().risk().pendingOccupation(), x, y);
    }
  }

  void botMove() {
    if (match == null || match.isDone() || match.isHumanTurn()) {
      return;
    }
    var move = match.step();
    hint = "";
    lastHumanMove = null;
    lastMoveOutlines = MoveHighlight.of(move, env());
    skipForcedPhases();
    render();
    scheduleBotMove();
  }

  /** The bots move one at a time, at the chosen speed, as long as it is not paused and no human has to move. */
  private void scheduleBotMove() {
    if (match == null || ! playButton.isSelected() || match.isDone() || match.isHumanTurn()) {
      return;
    }
    // from about a second between moves, to as fast as possible
    double fraction = 1 - speedSlider.getValue() / 100;
    timer.setDuration(Duration.millis(5 + 1000 * fraction * fraction));
    timer.playFromStart();
  }

  // showing the state

  private void render() {
    // the selection of the human, with the outlines of the last move on top, until the next click
    var outlines = new HashMap<Territory, Color>();
    if (match.isHumanTurn()) {
      humanTurn.selection().forEach((territory, selection) ->
          outlines.put(territory, selection == HumanTurn.Selection.SOURCE ? Color.BLACK : Color.DARKGRAY));
    }
    outlines.putAll(lastMoveOutlines);
    worldView.setMarkedTerritories(outlines);
    updateStatus();
    updateControls();
  }

  private String describe(Player player) {
    return "%s (%s)".formatted(player.name(), setup.kinds().get(env().playerIndex(player)).label());
  }

  private void updateStatus() {
    var env = env();
    if (match.isDone()) {
      statusLabel.setText(env.winner() == null
          ? "Draw: the limit of %d turns was reached".formatted(env.maxTurns())
          : "%s won after %d turns".formatted(describe(env.winner()), env.turns()));
    } else {
      statusLabel.setText("Turn %d – %s, %s".formatted(env.turns() + 1, describe(env.currentPlayer()), env.phase().name().toLowerCase()));
    }
    playersBox.getChildren().clear();
    for (var player : env.players()) {
      var territories = env.risk().playerTerritories(player);
      var swatch = new Rectangle(14, 14, FxColors.of(player.color()));
      swatch.setStroke(Color.BLACK);
      var marker = ! match.isDone() && player == env.currentPlayer() ? "▶ " : "   ";
      var label = new Label("%s%s: %d territories, %d armies".formatted(marker, describe(player), territories.size(),
          territories.stream().mapToInt(Territory::numArmies).sum()));
      label.setGraphic(swatch);
      playersBox.getChildren().add(label);
    }
  }

  private void updateControls() {
    if (match == null) {
      return;
    }
    boolean human = match.isHumanTurn();
    stepButton.setDisable(playButton.isSelected() || match.isDone() || human);
    // the controls of a human are shown in games with a human, and used when it is the turn of one
    humanBar.setVisible(setup.hasHuman());
    humanBar.setManaged(setup.hasHuman());
    var phase = env().phase();
    if (worldView.isEditingLabels()) {
      instructionLabel.setText("Editing labels: drag a territory to move its number and name, and double-click to put them back. "
          + "Switch it off in the settings to save.");
    } else if (match.isDone()) {
      instructionLabel.setText(statusLabel.getText());
    } else if (human) {
      instructionLabel.setText(humanTurn.instructions() + (hint.isEmpty() ? "" : "\n" + hint));
    } else {
      instructionLabel.setText("Waiting for %s%s".formatted(describe(env().currentPlayer()), playButton.isSelected() ? "" : " – press Play or Step"));
    }
  }
}
