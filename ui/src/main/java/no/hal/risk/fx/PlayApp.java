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
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
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
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Screen;
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

  private AiNetworks aiNetworks;
  private BotMatch match;
  private HumanTurn humanTurn;
  private MapViewport mapViewport;
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

  // setting up a new game, on the table: the kind of each seat (null if it is empty), the map, and the buttons in the middle
  private final PlayerKind[] seatKinds = {PlayerKind.HUMAN, PlayerKind.AI, null, null};
  private GameMap chosenMap = GameMap.CLASSIC;
  private boolean setupMode;
  private Pane setupLayer;
  private Button startButton;
  private final List<ToggleButton> mapButtons = new ArrayList<>();
  // the bar at the top
  private Label statusLabel;
  private ToggleButton playButton;
  private Button stepButton;
  private Slider speedSlider;
  private PauseTransition timer;
  // the players, in the corners of the map as if they were around a table, and what to do now at the bottom
  private final List<Seat> seats = new ArrayList<>();

  /** The card of a player: colored as the player, with the kind of player, and the numbers of territories and armies. */
  private record Seat(HBox row, VBox box, Rectangle swatch, Label title, Label counts, Label hint, Button skip) {
  }

  private static final String HELP = "Tap a territory to do the usual thing: place all the armies, attack, or move as many armies as possible. "
      + "Hold on it to choose how many armies instead, in a menu. "
      + "The button next to the hint ends the attack, or skips the fortification, and so does the Enter key.\n\n"
      + "New game shows the four seats: tap one to choose who plays it, or to leave it empty, and choose the map in the middle.\n\n"
      + "On the map, zoom with Ctrl or Cmd and scroll, and move it with scroll or by dragging with the right button. Press 0 to fit it.";

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
    show(stage, options, true);
  }

  /**
   * Like {@link #show(Stage, Map)}, but closing the window only ends the whole program if {@code exitOnClose}.
   * A program with a window for each user, such as one running in a browser, must not end when one of them is closed.
   */
  public void show(Stage stage, Map<String, String> options, boolean exitOnClose) {
    show(stage, options, exitOnClose, false);
  }

  /**
   * Like {@link #show(Stage, Map, boolean)}, and with {@code fillWindow} the scene has no size of its own, but takes that of the window,
   * which is the screen on a phone, and the browser window in a browser, and follows it when it changes, for instance when the device is turned.
   */
  public void show(Stage stage, Map<String, String> options, boolean exitOnClose, boolean fillWindow) {
    // PyTorch scales badly beyond a few threads for a network of this size
    System.setProperty("ai.djl.pytorch.num_threads", System.getProperty("ai.djl.pytorch.num_threads", "4"));
    aiNetworks = new AiNetworks(options.containsKey("ai2") ? Path.of(options.get("ai2")) : null,
        options.containsKey("ai4") ? Path.of(options.get("ai4")) : null);
    stage.setScene(fillWindow ? new Scene(createContent()) : new Scene(createContent(), 1100, 860));
    stage.setTitle("RiskFx");
    stage.setOnCloseRequest(_ -> {
      timer.stop();
      if (exitOnClose) {
        Platform.exit();
        System.exit(0);
      }
    });
    stage.show();
    startNewGame();
  }

  /**
   * The user interface, without a game, which is started with {@link #startNewGame()}: a slim bar at the top, and the map filling
   * the rest, with the players in its corners as if they were around a table,
   * and a card for starting a new game, which is over the map only while it is wanted. What a human can do now is in the card of the player.
   */
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

    createControls();
    var root = new BorderPane();
    root.setTop(createBar());
    mapViewport = new MapViewport(mapPane);
    mapViewport.setContentBounds(worldView::shapeBounds);
    // the text and lines of the map keep their size on the screen when zooming, which gives room for more of the names
    mapViewport.scaleProperty().subscribe(scale -> worldView.setViewScale(scale.doubleValue()));

    var table = new AnchorPane(mapViewport);
    AnchorPane.setTopAnchor(mapViewport, 0.0);
    AnchorPane.setBottomAnchor(mapViewport, 0.0);
    AnchorPane.setLeftAnchor(mapViewport, 0.0);
    AnchorPane.setRightAnchor(mapViewport, 0.0);
    // the players, clockwise from the lower left: the first is where you would sit, the next across the table
    seats.clear();
    for (int i = 0; i < GameSetup.MAX_PLAYERS; i++) {
      var seat = createSeat(i);
      seats.add(seat);
      table.getChildren().add(seat.row());
      boolean bottom = i == 0 || i == 3;
      boolean left = i == 0 || i == 1;
      if (bottom) {
        AnchorPane.setBottomAnchor(seat.row(), 6.0);
      } else {
        AnchorPane.setTopAnchor(seat.row(), 6.0);
      }
      if (left) {
        AnchorPane.setLeftAnchor(seat.row(), 6.0);
      } else {
        AnchorPane.setRightAnchor(seat.row(), 6.0);
      }
    }
    // the map can't be moved out of sight: it must stay in relation to the rectangle that the players never cover
    mapViewport.setSafeArea(() -> safeArea(table));
    setupLayer = createSetupLayer();
    table.getChildren().add(setupLayer);
    AnchorPane.setTopAnchor(setupLayer, 0.0);
    AnchorPane.setBottomAnchor(setupLayer, 0.0);
    AnchorPane.setLeftAnchor(setupLayer, 0.0);
    AnchorPane.setRightAnchor(setupLayer, 0.0);
    root.setCenter(table);

    // Enter ends the attack or skips the fortification, which is also in the menus, and 0 fits the map to the window again
    root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.DIGIT0 && ! event.isShortcutDown()) {
        mapViewport.reset();
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.ENTER && ! setupMode && endPhase()) {
        event.consume();
      }
    });
    return root;
  }

  /** Ends the attack, or skips the fortification, for a human whose turn it is, which the button and the Enter key do. Returns whether it did. */
  private boolean endPhase() {
    if (match == null || ! match.isHumanTurn()) {
      return false;
    }
    var action = switch (env().phase()) {
      case ATTACKING -> new Action.EndAttack();
      case FORTIFYING -> new Action.SkipFortify();
      default -> null;
    };
    if (action == null) {
      return false;
    }
    numberMenu.hide();
    humanAction(action);
    return true;
  }

  /** The most each seat has been high, since the hint comes and goes, and the rectangle that is safe from the players must not. */
  private final double[] seatHeights = new double[GameSetup.MAX_PLAYERS];

  /**
   * The part of the table that no player covers, whoever's turn it is: what is left when the seats in the corners are taken away,
   * the cards on the left and right, and the rows, with the hint, at the top and bottom. A hint is as wide as the table is not, so
   * it is the height of the rows that is reserved for it, not the width. Null before the table has a size.
   */
  private Bounds safeArea(Pane table) {
    if (table.getWidth() <= 0 || table.getHeight() <= 0) {
      return null;
    }
    double margin = 8;
    double left = 0, right = 0, top = 0, bottom = 0;
    for (int i = 0; i < seats.size(); i++) {
      var seat = seats.get(i);
      seatHeights[i] = Math.max(seatHeights[i], seat.row().getHeight());
      boolean bottomSeat = i == 0 || i == 3, leftSeat = i == 0 || i == 1;
      double width = seat.box().getWidth() + 2 * margin, height = seatHeights[i] + 2 * margin;
      if (leftSeat) {
        left = Math.max(left, width);
      } else {
        right = Math.max(right, width);
      }
      if (bottomSeat) {
        bottom = Math.max(bottom, height);
      } else {
        top = Math.max(top, height);
      }
    }
    double width = table.getWidth() - left - right, height = table.getHeight() - top - bottom;
    // a table too small to have any such rectangle has a point in the middle, which is then what the map must have
    double x = width > 0 ? left : (left + table.getWidth() - right) / 2, y = height > 0 ? top : (top + table.getHeight() - bottom) / 2;
    return new BoundingBox(x, y, Math.max(width, 0), Math.max(height, 0));
  }

  /** The controls of the settings and of the bar, which are made first, since they work together. */
  private void createControls() {
    showNamesBox = new CheckBox("Show territory names");
    showNamesBox.setSelected(true);
    showNamesBox.selectedProperty().subscribe(show -> worldView.setShowNames(show));
    showRegionsBox = new CheckBox("Show regions");
    showRegionsBox.selectedProperty().subscribe(show -> worldView.setShowRegions(show));
    editLabelsBox = new CheckBox("Edit label places");
    editLabelsBox.setTooltip(new Tooltip("Drag the number and name of a territory to move them, and double-click to put them back. "
        + "When switched off, the places can be saved for the map generator. Only for maps that have a file for it."));
    editLabelsBox.selectedProperty().addListener((_, _, editing) -> editLabelsChanged(editing));
    keepArmiesBackBox = new CheckBox("Keep armies back");
    keepArmiesBackBox.setTooltip(new Tooltip("After a conquest, move in as many armies as you choose, but at least as many as dice were rolled. Bots move in all they can."));
    eliminationBonusBox = new CheckBox("Bonus for eliminating a player");
    eliminationBonusBox.setTooltip(new Tooltip("%d more armies the next turn for each player you eliminated.".formatted(Risk.ELIMINATION_BONUS)));
    conquestBonusBox = new CheckBox("Bonus after conquest");
    conquestBonusBox.setTooltip(new Tooltip("A turn gives %d armies and the continent bonuses, and %d more if a territory was conquered the turn before.".formatted(Risk.ALWAYS_REINFORCEMENT, Risk.CONQUEST_BONUS)));
    speedSlider = new Slider(0, 100, 70);
    speedSlider.setPrefWidth(90);
    speedSlider.setTooltip(new Tooltip("Speed of the bots"));
  }

  /** The slim bar: new game, what is happening, the speed of the bots and play, pause and step, help and settings. */
  private Parent createBar() {
    var newGameButton = new Button("New game");
    newGameButton.setOnAction(_ -> setSetupMode(! setupMode));
    statusLabel = new Label();
    statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold");
    statusLabel.setMinWidth(0);
    statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox.setHgrow(statusLabel, Priority.SOMETIMES);
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
    var helpButton = new Button("?");
    helpButton.setTooltip(new Tooltip("Help"));
    var helpPopup = popupWith(new Label(HELP), 300);
    helpButton.setOnAction(_ -> togglePopup(helpPopup, helpButton, 300));
    var bar = new HBox(8, newGameButton, statusLabel, spacer, speedSlider, playButton, stepButton, helpButton, createSettingsButton());
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(4, 8, 4, 8));
    return bar;
  }

  /** A popup with the content, in a frame, which closes when clicking outside it. */
  private static Popup popupWith(Label content, double width) {
    content.setWrapText(true);
    content.setMaxWidth(width);
    var frame = new VBox(content);
    frame.setPadding(new Insets(12));
    frame.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 4; -fx-border-radius: 4;");
    var popup = new Popup();
    popup.setAutoHide(true);
    popup.getContent().add(frame);
    return popup;
  }

  /** Shows a popup under a button, with their right edges aligned, or hides it if it is showing. */
  private static void togglePopup(Popup popup, Button button, double width) {
    if (popup.isShowing()) {
      popup.hide();
    } else {
      var bounds = button.localToScreen(button.getBoundsInLocal());
      // its right edge is where the button's is, and it is not off the left of the screen. A popup has no size until it is shown and
      // laid out, so the size it will get is asked of its content.
      popup.show(button, Math.max(0, bounds.getMaxX() - popupSize(popup)[0]), bounds.getMaxY() + 4);
    }
  }

  /** The width and height that the content of a popup wants, since the popup itself has no size until it has been shown and laid out. */
  private static double[] popupSize(Popup popup) {
    if (popup.getContent().getFirst() instanceof javafx.scene.layout.Region content) {
      content.applyCss();
      return new double[] {content.prefWidth(-1), content.prefHeight(-1)};
    }
    return new double[] {0, 0};
  }

  /**
   * The cog button in the bar, which shows the settings in a popup that closes when clicking outside it: the view, which is changed
   * during a game, and the rules, which are read when a new game starts. The groups are side by side when the window is wide,
   * and above each other when it is tall.
   */
  private Button createSettingsButton() {
    var button = new Button();
    button.setGraphic(Icons.gear(16, Icons.DARK));
    button.setTooltip(new Tooltip("Settings"));
    var note = new Label("Read when a new game starts.");
    note.setStyle("-fx-text-fill: gray;");
    var frame = new VBox();
    frame.setPadding(new Insets(12));
    frame.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 4; -fx-border-radius: 4;");
    var popup = new Popup();
    popup.setAutoHide(true);
    popup.getContent().add(frame);
    button.setOnAction(_ -> {
      var view = new VBox(8, groupTitle("View"), showNamesBox, showRegionsBox, editLabelsBox);
      var rules = new VBox(8, groupTitle("Rules"), keepArmiesBackBox, conquestBonusBox, eliminationBonusBox, note);
      var scene = button.getScene();
      boolean wide = scene != null && scene.getWidth() > scene.getHeight();
      frame.getChildren().setAll(wide ? new HBox(24, view, rules) : new VBox(16, view, rules));
      togglePopup(popup, button, 190);
    });
    return button;
  }

  private static Label groupTitle(String text) {
    var title = new Label(text);
    title.setStyle("-fx-font-weight: bold;");
    return title;
  }

  /** The card of a player, with the kind, and the numbers of territories and armies. */
  private Seat createSeat(int index) {
    var swatch = new Rectangle(14, 14, FxColors.of(RiskConfig.playerColor(index)));
    swatch.setStroke(Color.BLACK);
    var title = new Label();
    title.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
    var counts = new Label();
    counts.setStyle("-fx-font-size: 11;");
    // what the player can do now, beside the card of the player whose turn it is, if it is a human, so it stands out and shows whose
    // turn it is: to the right of a card in a left corner, and to the left of one in a right corner, where there is room to grow
    var hint = new Label();
    hint.setWrapText(true);
    hint.setMaxWidth(300);
    hint.setStyle("-fx-background-color: rgba(30, 30, 30, 0.85); -fx-text-fill: white; -fx-font-size: 17; -fx-font-weight: bold; "
        + "-fx-padding: 8 14 8 14; -fx-background-radius: 12;");
    hint.setVisible(false);
    hint.setManaged(false);
    var box = new VBox(1, new HBox(5, swatch, title), counts);
    box.setPadding(new Insets(3, 8, 3, 8));
    boolean left = index == 0 || index == 1;
    // the button ends the attack or skips the fortification, so it is only there in those phases, for a human
    var skip = new Button();
    skip.setGraphic(Icons.skip(16, Color.WHITE));
    skip.setStyle("-fx-background-color: rgba(30, 30, 30, 0.85); -fx-background-radius: 20; -fx-min-width: 44; -fx-min-height: 44; "
        + "-fx-max-width: 44; -fx-max-height: 44;");
    skip.setVisible(false);
    skip.setManaged(false);
    skip.setOnAction(_ -> endPhase());
    // the button comes after the hint, reading from the left, on every seat: so next to the card on the right-hand seats
    var row = left ? new HBox(8, box, hint, skip) : new HBox(8, hint, skip, box);
    // the card and the hint are centered on each other, and the map under them gets the taps, but the button gets its own
    row.setAlignment(left ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
    // the card lets taps through to the map, except when setting up a game, when a tap on it is to choose who plays that seat
    box.setMouseTransparent(true);
    box.setOnMouseClicked(_ -> {
      if (setupMode) {
        showKindPopup(index);
      }
    });
    hint.setMouseTransparent(true);
    row.setPickOnBounds(false);
    row.setVisible(false);
    return new Seat(row, box, swatch, title, counts, hint, skip);
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

  // setup

  /** What is over the table when setting up a game: an explanation, the choice of map, and buttons for starting the game and for cancelling. */
  private Pane createSetupLayer() {
    var group = new ToggleGroup();
    var maps = new HBox(8);
    maps.setAlignment(Pos.CENTER);
    for (var map : GameMap.all()) {
      var mapButton = new ToggleButton(map.name());
      // a picture of the map above its name
      mapButton.setGraphic(MapThumbnail.of(map, 110, 72));
      mapButton.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
      mapButton.setToggleGroup(group);
      mapButton.setUserData(map);
      mapButton.setMinWidth(130);
      mapButton.setOnAction(_ -> chosenMap = map);
      mapButtons.add(mapButton);
      maps.getChildren().add(mapButton);
    }
    // one map is always chosen
    group.selectedToggleProperty().addListener((_, old, now) -> {
      if (now == null && old != null) {
        old.setSelected(true);
      }
    });
    startButton = new Button();
    startButton.setGraphic(Icons.check(18, Color.WHITE));
    startButton.setStyle("-fx-background-color: #2e7d32; -fx-background-radius: 22; -fx-min-width: 56; -fx-min-height: 44;");
    startButton.setTooltip(new Tooltip("Start the game"));
    startButton.setOnAction(_ -> {
      startNewGame();
      setSetupMode(false);
    });
    var cancelButton = new Button();
    cancelButton.setGraphic(Icons.cross(16, Color.WHITE));
    cancelButton.setStyle("-fx-background-color: #b71c1c; -fx-background-radius: 22; -fx-min-width: 56; -fx-min-height: 44;");
    cancelButton.setTooltip(new Tooltip("Keep the game that is going"));
    cancelButton.setOnAction(_ -> {
      showSetup(setup);
      setSetupMode(false);
    });
    var buttons = new HBox(16, cancelButton, startButton);
    buttons.setAlignment(Pos.CENTER);
    // no frame around any of it, so it doesn't look like a dialog: the explanation is a pill like the hint at the top, below the seats there,
    // the map choice is in the middle, and the buttons are at the bottom, above the seats there
    var explanation = new Label("Tap a corner to choose who plays");
    explanation.setWrapText(true);
    explanation.setMaxWidth(320);
    // a wrapped label otherwise gets the height of one line from a layout that doesn't know the width, and cuts the text off
    explanation.setMinHeight(Region.USE_PREF_SIZE);
    explanation.setTextAlignment(TextAlignment.CENTER);
    explanation.setStyle("-fx-background-color: rgba(30, 30, 30, 0.85); -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; "
        + "-fx-padding: 6 14 6 14; -fx-background-radius: 12;");
    explanation.setMouseTransparent(true);
    var layer = new BorderPane();
    layer.setTop(explanation);
    BorderPane.setAlignment(explanation, Pos.TOP_CENTER);
    BorderPane.setMargin(explanation, new Insets(58, 8, 0, 8));
    layer.setCenter(maps);
    layer.setBottom(buttons);
    BorderPane.setAlignment(buttons, Pos.BOTTOM_CENTER);
    BorderPane.setMargin(buttons, new Insets(0, 0, 58, 0));
    layer.setPickOnBounds(false);
    layer.setVisible(false);
    return layer;
  }

  /**
   * Switches to setting up a new game on the table, where the seats show who plays them, and can be tapped to change that, or back
   * to the game. The game that is going on is not changed until a new one is started, and the bots wait.
   */
  void setSetupMode(boolean on) {
    if (setupMode == on) {
      return;
    }
    setupMode = on;
    setupLayer.setVisible(on);
    for (int i = 0; i < seats.size(); i++) {
      seats.get(i).box().setMouseTransparent(! on);
    }
    if (on) {
      timer.stop();
      mapButtons.forEach(mapButton -> mapButton.setSelected(mapButton.getUserData() == chosenMap));
      numberMenu.hide();
    } else {
      scheduleBotMove();
    }
    updateSetupControls();
    if (match != null) {
      render();
    }
  }

  private int takenSeats() {
    return (int) java.util.Arrays.stream(seatKinds).filter(java.util.Objects::nonNull).count();
  }

  /** A game needs two players, so it can only be started when two seats are taken. */
  private void updateSetupControls() {
    startButton.setDisable(takenSeats() < GameSetup.MIN_PLAYERS);
  }

  /**
   * A popup of the choices for a seat, in two columns of large buttons next to the card of the seat: who plays it, or that it is empty.
   * The default of the seat comes first, since that is the usual choice. It is not a list, which scrolls when there is not room for it,
   * and on a touch screen a tap that moves a little is then a scroll and not a choice.
   */
  private void showKindPopup(int seat) {
    var kinds = new ArrayList<>(List.of(PlayerKind.values()));
    var seatDefault = GameSetup.SEAT_DEFAULTS.get(seat);
    kinds.remove(seatDefault);
    kinds.addFirst(seatDefault);
    var options = new ArrayList<PlayerKind>(kinds);
    // an empty seat is null
    options.add(null);
    var popup = new Popup();
    popup.setAutoHide(true);
    var grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));
    grid.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 6; -fx-border-radius: 6;");
    for (int i = 0; i < options.size(); i++) {
      var kind = options.get(i);
      var choice = new Button(kind == null ? "Off" : kind.label());
      choice.setMinSize(130, 44);
      if (kind == seatKinds[seat]) {
        choice.setStyle("-fx-font-weight: bold; -fx-border-color: #333333; -fx-border-width: 2;");
      }
      choice.setOnAction(_ -> {
        seatKinds[seat] = kind;
        popup.hide();
        updateSetupControls();
        updateStatus();
      });
      grid.add(choice, i % 2, i / 2);
    }
    popup.getContent().add(grid);
    var box = seats.get(seat).box();
    var bounds = box.localToScreen(box.getBoundsInLocal());
    // below a card at the top and above one at the bottom, and on the same side as the card, kept on the screen
    boolean bottom = seat == 0 || seat == 3, left = seat == 0 || seat == 1;
    var size = popupSize(popup);
    var screens = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1);
    var visible = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
    double x = left ? bounds.getMinX() : bounds.getMaxX() - size[0];
    double y = bottom ? bounds.getMinY() - size[1] - 4 : bounds.getMaxY() + 4;
    popup.show(box, Math.max(visible.getMinX(), Math.min(x, visible.getMaxX() - size[0])),
        Math.max(visible.getMinY(), Math.min(y, visible.getMaxY() - size[1])));
  }

  /** Shows a setup in the seats: the kind of each. */
  private void showSetup(GameSetup shown) {
    System.arraycopy(shown.seatKinds(), 0, seatKinds, 0, seatKinds.length);
    if (startButton != null) {
      updateSetupControls();
    }
  }

  /** The setup as chosen in the seats. */
  private GameSetup chosenSetup() {
    return GameSetup.ofSeats(seatKinds);
  }

  /** Starts a game with the players as chosen in the seats, and the map. */
  void startNewGame() {
    var chosen = chosenSetup();
    var rules = new Rules(keepArmiesBackBox.isSelected(), conquestBonusBox.isSelected(), eliminationBonusBox.isSelected());
    startGame(chosen, chosen.fortifyMode(), rules, chosenMap, nextSeed++);
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
    chosenMap = map;
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
    // a new map is fitted to the window, and centered
    mapViewport.reset();
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
    if (setupMode) {
      return;
    }
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
        // there is only one thing to do, so no menu: an attack, where the menu for the armies to move in after a conquest is shown if the rule
        // lets the player choose, or selecting or deselecting a territory to attack from. Ending the attack is the button for it.
        boolean attack = territory.occupant() != env().currentPlayer() && humanTurn.selectedFrom() != null;
        if (attack) {
          attackOnce(territory, true, x, y);
        } else {
          territoryDefault(territory);
        }
      }
      case FORTIFYING -> {
        if (humanTurn.canFortifyTo(territory)) {
          select(territory);
          render();
          int max = humanTurn.maxFortifyArmies();
          numberMenu.show(owner, x, y, 1, max, max,
              n -> n == max ? "Move all (%d)".formatted(n) : "Move %d".formatted(n),
              n -> humanAction(humanTurn.fortify(n)));
        } else {
          // selecting or deselecting the territory to move armies from is all there is, so no menu. Skipping is the button for it.
          territoryDefault(territory);
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
    // when setting up a new game, the map is quiet: light grey, without the outlines of the game that is going on
    worldView.setDimmed(setupMode);
    worldView.setMarkedTerritories(setupMode ? Map.of() : outlines);
    updateStatus();
    updateControls();
  }

  private String describe(Player player) {
    return "%s (%s)".formatted(player.name(), setup.kinds().get(env().playerIndex(player)).label());
  }

  /** What is happening, in the bar, and the players in their corners. */
  private void updateStatus() {
    var env = env();
    if (match.isDone()) {
      statusLabel.setText(env.winner() == null
          ? "Draw: the limit of %d turns was reached".formatted(env.maxTurns())
          : "%s won after %d turns".formatted(describe(env.winner()), env.turns()));
    } else {
      statusLabel.setText("Turn %d · %s · %s".formatted(env.turns() + 1, env.currentPlayer().name(), env.phase().name().toLowerCase()));
    }
    int toPlace = env.risk().currentTurnState() instanceof TurnState.Reinforcing reinforcing ? reinforcing.totalArmies() : 0;
    for (int i = 0; i < seats.size(); i++) {
      var seat = seats.get(i);
      var color = RiskConfig.playerColor(i);
      if (setupMode) {
        // who plays the seat in the game that is being set up, which can be changed by tapping the card
        var kind = seatKinds[i];
        seat.row().setVisible(true);
        seat.title().setText("Player %d · %s".formatted(i + 1, kind == null ? "Off" : kind.label()));
        seat.counts().setText("Tap to change");
        seat.box().setOpacity(kind == null ? 0.55 : 1.0);
        seat.box().setStyle(seatStyle(color, 3));
        continue;
      }
      var player = playerOfSeat(i);
      seat.row().setVisible(player != null);
      if (player == null) {
        continue;
      }
      var territories = env.risk().playerTerritories(player);
      boolean current = ! match.isDone() && player == env.currentPlayer();
      seat.title().setText("%s · %s".formatted(player.name(), setup.kinds().get(env.playerIndex(player)).label()));
      seat.counts().setText("%d terr. · %d armies%s".formatted(territories.size(),
          territories.stream().mapToInt(Territory::numArmies).sum(), current && toPlace > 0 ? " · +" + toPlace : ""));
      // the player whose turn it is has a thick frame in the color of the player, and the others fade
      boolean alive = ! territories.isEmpty();
      seat.box().setOpacity(alive ? (current ? 1.0 : 0.8) : 0.45);
      seat.box().setStyle(seatStyle(color, current ? 4 : 1));
    }
  }

  private static String seatStyle(no.hal.risk.PlayerColor color, int frame) {
    return "-fx-background-color: rgba(255, 255, 255, 0.88); -fx-background-radius: 8; -fx-border-radius: 8; "
        + "-fx-border-color: %s; -fx-border-width: %d;".formatted(css(color), frame);
  }

  /** The player in the seat in the game that is going on, or null if the seat is empty. */
  private Player playerOfSeat(int seat) {
    int player = setup.playerOfSeat(seat);
    return player < 0 ? null : env().players().get(player);
  }

  private static String css(no.hal.risk.PlayerColor color) {
    var fx = FxColors.of(color);
    return "rgb(%d, %d, %d)".formatted((int) Math.round(fx.getRed() * 255), (int) Math.round(fx.getGreen() * 255), (int) Math.round(fx.getBlue() * 255));
  }

  private void updateControls() {
    if (match == null) {
      return;
    }
    boolean human = match.isHumanTurn();
    stepButton.setDisable(playButton.isSelected() || match.isDone() || human);
    // what a human can do, and a message about why something didn't work, in the card of the human whose turn it is
    var current = match.isDone() ? null : env().currentPlayer();
    for (int i = 0; i < seats.size(); i++) {
      var seat = seats.get(i);
      boolean here = human && ! setupMode && current != null && playerOfSeat(i) == current;
      seat.hint().setVisible(here);
      seat.hint().setManaged(here);
      // ending the attack, or skipping the fortification, has no argument, so it is a button, and there is nothing to skip when reinforcing
      var phase = env().phase();
      boolean canSkip = here && (phase == Phase.ATTACKING || phase == Phase.FORTIFYING);
      seat.skip().setVisible(canSkip);
      seat.skip().setManaged(canSkip);
      if (canSkip) {
        seat.skip().setTooltip(new Tooltip(phase == Phase.ATTACKING ? "End the attack" : "Skip the fortification"));
      }
      if (here) {
        seat.hint().setText(humanTurn.shortInstructions() + (hint.isEmpty() ? "" : "\n" + hint));
      }
    }
    if (worldView.isEditingLabels()) {
      statusLabel.setText("Editing labels: drag to move, double-click to put back. Switch off in the settings to save.");
    }
  }
}
