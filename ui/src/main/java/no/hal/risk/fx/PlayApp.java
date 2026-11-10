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
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Text;
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
import no.hal.risk.ai.Bots;
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
  /** Whether the game that was started has been stopped, which is like it being over: nothing more is played, and a new one can be set up. */
  private boolean stopped;
  /** Whether the bots play on by themselves, or are paused. */
  private boolean playing = true;
  private Label betweenGamesHint;
  private Button mapButton;
  private java.net.URL stylesheet;
  // the bar at the top
  private Label statusLabel;
  private Button playButton;
  private Button stopButton;
  private Slider speedSlider;
  private PauseTransition timer;
  // the players, in the corners of the map as if they were around a table, and what to do now at the bottom
  private final List<Seat> seats = new ArrayList<>();

  /** The card of a player: colored as the player, with the kind of player, and the numbers of territories and armies. */
  private record Seat(HBox row, VBox box, Rectangle swatch, HBox icon, Label title, Label counts, Label hint, Button skip) {
  }

  private static final String HELP = "Tap a territory to do the usual thing: place all the armies, attack, or move as many armies as possible. "
      + "Hold on it to choose how many armies instead, in a menu. "
      + "The button next to the hint ends the attack, or skips the fortification, and so does the Enter key.\n\n"
      + "Play starts a game, and then pauses it and resumes it. Stop ends the game. Between games, tap a player to choose who plays it, or to "
      + "turn it off, and the map button to choose the map. During a game, tap a player to swap who plays it.\n\n"
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
    try {
      showWindow(stage, options, exitOnClose, fillWindow);
    } catch (Throwable e) {
      // an error at the start, which on a phone would be a black window, and nowhere to read what went wrong, is shown instead
      e.printStackTrace();
      stage.setScene(new Scene(failureContent(e)));
      stage.show();
    }
  }

  /** What is shown if the window could not be made: the error, with what caused it, to read, and to tell. */
  Parent failureContent(Throwable error) {
    var text = new java.io.StringWriter();
    error.printStackTrace(new java.io.PrintWriter(text));
    var title = new Label("RiskFx could not start");
    title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #b71c1c;");
    var details = new Label(text.toString());
    details.setWrapText(true);
    details.setStyle("-fx-font-family: monospace; -fx-font-size: 14;");
    var scroll = new javafx.scene.control.ScrollPane(details);
    scroll.setFitToWidth(true);
    var box = new VBox(8, title, scroll);
    VBox.setVgrow(scroll, Priority.ALWAYS);
    box.setPadding(new Insets(12));
    box.setStyle("-fx-background-color: white;");
    return box;
  }

  private void showWindow(Stage stage, Map<String, String> options, boolean exitOnClose, boolean fillWindow) {
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
    // the stylesheet, which is also a resource of the application that must be in the native build: a missing one is only a plainer look
    stylesheet = PlayApp.class.getResource("/riskfx.css");
    if (stylesheet != null) {
      root.getStylesheets().add(stylesheet.toExternalForm());
    }
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
    // what to do between games, which is shown only then
    betweenGamesHint = new Label("Tap a player to change who plays, or the map button above to change the map. Then press Play.");
    betweenGamesHint.setWrapText(true);
    betweenGamesHint.setMaxWidth(340);
    betweenGamesHint.setMinHeight(Region.USE_PREF_SIZE);
    betweenGamesHint.setTextAlignment(TextAlignment.CENTER);
    betweenGamesHint.setStyle("-fx-background-color: rgba(30, 30, 30, 0.85); -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; "
        + "-fx-padding: 6 14 6 14; -fx-background-radius: 12;");
    var hintRow = new HBox(betweenGamesHint);
    hintRow.setAlignment(Pos.TOP_CENTER);
    hintRow.setMouseTransparent(true);
    hintRow.setPickOnBounds(false);
    table.getChildren().add(hintRow);
    // below the players in the corners at the top
    AnchorPane.setTopAnchor(hintRow, 58.0);
    AnchorPane.setLeftAnchor(hintRow, 8.0);
    AnchorPane.setRightAnchor(hintRow, 8.0);
    root.setCenter(table);

    // Enter ends the attack or skips the fortification, which is also in the menus, and 0 fits the map to the window again
    root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.DIGIT0 && ! event.isShortcutDown()) {
        mapViewport.reset();
        event.consume();
        return;
      }
      if (event.getCode() == KeyCode.ENTER && ! idle() && endPhase()) {
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
    speedSlider.setPrefWidth(160);
    speedSlider.setTooltip(new Tooltip("Speed of the bots"));
  }

  /**
   * The slim bar: play, which starts a game and then pauses it and resumes it, stop, what is happening, the speed of the bots,
   * the map to play on, help and settings.
   */
  private Parent createBar() {
    statusLabel = new Label();
    statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold");
    statusLabel.setMinWidth(0);
    statusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox.setHgrow(statusLabel, Priority.SOMETIMES);
    // starts a game when there isn't one, and otherwise pauses the bots and resumes them
    playButton = new Button();
    playButton.setMinWidth(92);
    playButton.setOnAction(_ -> {
      if (idle()) {
        startNewGame();
      } else {
        setPlaying(! playing);
      }
    });
    stopButton = new Button();
    stopButton.setGraphic(Icons.of(Icons.STOP, 16, Icons.DARK));
    stopButton.setTooltip(new Tooltip("Stop the game"));
    stopButton.setOnAction(_ -> confirmStop());
    mapButton = new Button();
    mapButton.setOnAction(_ -> showMapPopup());
    updateMapButton();
    var helpButton = new Button();
    helpButton.setGraphic(Icons.of(Icons.HELP, 18, Icons.DARK));
    helpButton.setTooltip(new Tooltip("Help"));
    var helpPopup = popupWith(new Label(HELP), 300);
    helpButton.setOnAction(_ -> showTextPopup(helpPopup, helpButton, 300));
    var bar = new HBox(8, playButton, stopButton, statusLabel, spacer, mapButton, helpButton, createSettingsButton());
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(4, 8, 4, 8));
    updateTransport();
    return bar;
  }

  /**
   * No game is going: none has been started, or the one that was started is over or stopped. Then the players and the map can be set,
   * and Play starts a new game.
   */
  private boolean idle() {
    return match == null || match.isDone() || stopped;
  }

  /** Lets the bots play on, or pauses them. A human can still make their own moves in a paused game. */
  private void setPlaying(boolean play) {
    playing = play;
    if (play) {
      scheduleBotMove();
    } else {
      timer.stop();
    }
    updateTransport();
    updateControls();
  }

  /** Play, pause or resume, depending on the game, and what is possible: only with players, only the map between games. */
  private void updateTransport() {
    if (playButton == null) {
      return;
    }
    boolean idle = idle();
    if (idle) {
      playButton.setText("Play");
      playButton.setGraphic(Icons.of(Icons.PLAY, 16, Icons.DARK));
      playButton.setTooltip(new Tooltip("Start a game with the players that are set"));
    } else if (playing) {
      playButton.setText("Pause");
      playButton.setGraphic(Icons.of(Icons.PAUSE, 16, Icons.DARK));
      playButton.setTooltip(new Tooltip("Pause the bots"));
    } else {
      playButton.setText("Resume");
      playButton.setGraphic(Icons.of(Icons.PLAY, 16, Icons.DARK));
      playButton.setTooltip(new Tooltip("Let the bots go on"));
    }
    // a game needs two players, so it can only be started when two seats are taken
    playButton.setDisable(idle && takenSeats() < GameSetup.MIN_PLAYERS);
    stopButton.setDisable(idle);
    mapButton.setDisable(! idle);
    if (betweenGamesHint != null) {
      betweenGamesHint.setVisible(idle);
      betweenGamesHint.setManaged(idle);
    }
  }

  private int takenSeats() {
    return (int) java.util.Arrays.stream(seatKinds).filter(java.util.Objects::nonNull).count();
  }

  /** Stops the game that is going, after asking, since it can't be taken back. */
  private void confirmStop() {
    if (idle()) {
      return;
    }
    var end = new Button("End the game");
    var keep = new Button("Keep playing");
    for (var button : new Button[] {end, keep}) {
      button.setMinHeight(40);
    }
    var question = new VBox(10, new Label("End this game?"), new HBox(8, end, keep));
    question.setPadding(new Insets(12));
    question.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 4; -fx-border-radius: 4;");
    var popup = new Popup();
    popup.setAutoHide(true);
    popup.getContent().add(question);
    end.setOnAction(_ -> {
      popup.hide();
      stopGame();
    });
    keep.setOnAction(_ -> popup.hide());
    togglePopup(popup, stopButton, 0);
  }

  /** Stops the game that is going: it stays as it is on the table, and a new one can be set up and started. */
  void stopGame() {
    if (idle()) {
      return;
    }
    stopped = true;
    timer.stop();
    numberMenu.hide();
    humanTurn.clearSelection();
    hint = "";
    render();
  }

  /** The map button in the bar: a picture of the chosen map, and its name when it is asked. */
  private void updateMapButton() {
    try {
      mapButton.setGraphic(MapThumbnail.of(chosenMap, 36, 24));
    } catch (RuntimeException | LinkageError e) {
      // without a picture it has the name
      e.printStackTrace();
      mapButton.setText(chosenMap.name());
    }
    mapButton.setTooltip(new Tooltip("Map: " + chosenMap.name()));
  }

  /** A popup of the maps, with a picture of each and its name, to choose where the next game is played. */
  private void showMapPopup() {
    var popup = new Popup();
    popup.setAutoHide(true);
    var group = new ToggleGroup();
    var maps = new HBox(8);
    maps.setPadding(new Insets(10));
    maps.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 6; -fx-border-radius: 6;");
    // the popup is a window of its own, which doesn't have the stylesheet of the application
    if (stylesheet != null) {
      maps.getStylesheets().add(stylesheet.toExternalForm());
    }
    for (var map : GameMap.all()) {
      var choice = new ToggleButton(map.name());
      choice.getStyleClass().add("map-button");
      try {
        choice.setGraphic(MapThumbnail.of(map, 110, 72));
        choice.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
      } catch (RuntimeException | LinkageError e) {
        e.printStackTrace();
      }
      choice.setToggleGroup(group);
      choice.setSelected(map == chosenMap);
      choice.setMinWidth(130);
      choice.setOnAction(_ -> {
        chosenMap = map;
        updateMapButton();
        popup.hide();
      });
      maps.getChildren().add(choice);
    }
    // one map is always chosen
    group.selectedToggleProperty().addListener((_, old, now) -> {
      if (now == null && old != null) {
        old.setSelected(true);
      }
    });
    popup.getContent().add(maps);
    togglePopup(popup, mapButton, 0);
  }

  /** A popup with the content, in a frame, which closes when clicking outside it. */
  private static Popup popupWith(Label content, double width) {
    content.setWrapText(true);
    // a width, to have the height it gets when wrapped there
    content.setPrefWidth(width);
    content.setMaxWidth(width);
    // the text is in a scroll pane, so that all of it can be reached when it doesn't fit in the window, see showTextPopup
    var scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setPrefViewportWidth(width + 14);
    scroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
    var frame = new VBox(scroll);
    frame.setPadding(new Insets(12));
    frame.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 4; -fx-border-radius: 4;");
    var popup = new Popup();
    popup.setAutoHide(true);
    popup.getContent().add(frame);
    return popup;
  }

  /** Shows (or hides) a popup made by popupWith under a button, with the text no higher than there is room for in the window. */
  private static void showTextPopup(Popup popup, Button button, double width) {
    if (! popup.isShowing() && button.getScene() != null) {
      var frame = (VBox) popup.getContent().getFirst();
      var scroll = (ScrollPane) frame.getChildren().getFirst();
      // a scroll pane is 100 high unless told otherwise: it is as high as the text, which is measured as wrapped to the width (a label is a little higher than a text), but not
      // higher than there is room for, which is where the scroll bar comes from
      var content = (Label) scroll.getContent();
      var measure = new Text(content.getText());
      measure.setFont(content.getFont());
      measure.setWrappingWidth(width);
      double room = button.getScene().getHeight() - button.localToScene(button.getBoundsInLocal()).getMaxY() - 4
          - frame.getPadding().getTop() - frame.getPadding().getBottom() - 8;
      scroll.setPrefViewportHeight(Math.max(60, Math.min(measure.getLayoutBounds().getHeight() * 1.1 + 6, room)));
    }
    togglePopup(popup, button, width);
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
    button.setGraphic(Icons.of(Icons.SETTINGS, 18, Icons.DARK));
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
      var view = new VBox(8, groupTitle("View"), showNamesBox, showRegionsBox, editLabelsBox, new Label("Speed of the bots"), speedSlider);
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
    // the icon of what controls the player, which changes with the player
    var icon = new HBox();
    icon.setAlignment(Pos.CENTER);
    var box = new VBox(1, new HBox(5, swatch, icon, title), counts);
    box.setPadding(new Insets(3, 8, 3, 8));
    boolean left = index == 0 || index == 1;
    // the button ends the attack or skips the fortification, so it is only there in those phases, for a human
    var skip = new Button();
    skip.setGraphic(Icons.of(Icons.SKIP, 20, Color.WHITE));
    skip.setStyle("-fx-background-color: rgba(30, 30, 30, 0.85); -fx-background-radius: 20; -fx-min-width: 44; -fx-min-height: 44; "
        + "-fx-max-width: 44; -fx-max-height: 44;");
    skip.setVisible(false);
    skip.setManaged(false);
    skip.setOnAction(_ -> endPhase());
    // the button comes after the hint, reading from the left, on every seat: so next to the card on the right-hand seats
    var row = left ? new HBox(8, box, hint, skip) : new HBox(8, hint, skip, box);
    // the card and the hint are centered on each other, and the map under them gets the taps, but the button gets its own
    row.setAlignment(left ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
    // a tap on the card is to choose who plays that seat: between games, to set up the next, and in the game, to swap a player
    box.setOnMouseClicked(_ -> {
      if (match != null && ! worldView.isEditingLabels()) {
        showKindPopup(index, ! idle());
      }
    });
    hint.setMouseTransparent(true);
    row.setPickOnBounds(false);
    row.setVisible(false);
    return new Seat(row, box, swatch, icon, title, counts, hint, skip);
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

  /**
   * A popup of the choices for a seat, in two columns of large buttons next to the card of the seat: who plays it, or that it is empty.
   * The default of the seat comes first, since that is the usual choice. It is not a list, which scrolls when there is not room for it,
   * and on a touch screen a tap that moves a little is then a scroll and not a choice.
   */
  private void showKindPopup(int seat, boolean inGame) {
    var kinds = new ArrayList<>(List.of(PlayerKind.values()));
    var seatDefault = GameSetup.SEAT_DEFAULTS.get(seat);
    kinds.remove(seatDefault);
    kinds.addFirst(seatDefault);
    var options = new ArrayList<PlayerKind>(kinds);
    // an empty seat is null, which is only between games, since a player can't leave a game that is going
    if (! inGame) {
      options.add(null);
    }
    var popup = new Popup();
    popup.setAutoHide(true);
    if (inGame) {
      // the bots are stopped while choosing, and go on when it is done, or the popup is closed without choosing, unless they were stopped
      boolean botsPlaying = playing && ! match.isHumanTurn();
      if (botsPlaying) {
        setPlaying(false);
      }
      popup.setOnHidden(_ -> {
        if (botsPlaying) {
          setPlaying(true);
        }
      });
    }
    var grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));
    grid.setStyle("-fx-background-color: -fx-background; -fx-border-color: gray; -fx-background-radius: 6; -fx-border-radius: 6;");
    for (int i = 0; i < options.size(); i++) {
      var kind = options.get(i);
      var choice = new Button(kind == null ? "Off" : kind.label());
      choice.setGraphic(Icons.forKind(kind, 20, Icons.DARK));
      choice.setGraphicTextGap(8);
      choice.setAlignment(Pos.CENTER_LEFT);
      choice.setMinSize(150, 44);
      if (kind == seatKinds[seat]) {
        choice.setStyle("-fx-font-weight: bold; -fx-border-color: #333333; -fx-border-width: 2;");
      }
      choice.setOnAction(_ -> {
        if (inGame) {
          changePlayer(seat, kind);
        } else {
          seatKinds[seat] = kind;
          updateTransport();
          updateStatus();
        }
        popup.hide();
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

  /**
   * Gives the player in a seat another kind in the game that is going on, which takes over from the next action: a bot is swapped for
   * another, or for a human, or a human for a bot, wherever in a turn the game is.
   */
  private void changePlayer(int seat, PlayerKind kind) {
    int player = setup.playerOfSeat(seat);
    if (match == null || player < 0 || kind == null || kind == setup.kinds().get(player)) {
      return;
    }
    try {
      match.replaceBot(env().players().get(player), createBot(kind, setup.playerCount(), currentMap));
    } catch (IOException e) {
      hint = "Could not change the player: " + e.getMessage();
      render();
      return;
    }
    var kinds = new ArrayList<>(setup.kinds());
    kinds.set(player, kind);
    setup = new GameSetup(kinds, setup.seats());
    showSetup(setup);
    // what a human had selected is not the new player's
    humanTurn.clearSelection();
    hint = "";
    render();
    scheduleBotMove();
  }

  /** Shows a setup in the seats: the kind of each. */
  private void showSetup(GameSetup shown) {
    System.arraycopy(shown.seatKinds(), 0, seatKinds, 0, seatKinds.length);
    updateTransport();
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

  /** The bot that is what the kind says, in a game of this number of players on the map. */
  private Bot createBot(PlayerKind kind, int playerCount, GameMap map) throws IOException {
    return switch (kind) {
      case HUMAN -> new HumanBot();
      // the rule-based bots are by name
      case GREEDY, CAUTIOUS, DENIER, ERRATIC -> Bots.byName(kind.botName());
      case AI -> aiNetworks.botFor(playerCount, map);
    };
  }

  void startGame(GameSetup newSetup, FortifyMode fortifyMode, Rules rules, GameMap map, long seed) {
    timer.stop();
    // a new map has its own labels, so editing the labels of the last one ends, with the question whether to save
    editLabelsBox.setSelected(false);
    currentMap = map;
    chosenMap = map;
    updateMapButton();
    editLabelsBox.setDisable(map.labelOffsetsFile() == null);
    var bots = new ArrayList<Bot>();
    for (var kind : newSetup.kinds()) {
      try {
        bots.add(createBot(kind, newSetup.playerCount(), map));
      } catch (IOException e) {
        statusLabel.setText("No AI network for %d players: could not read %s (%s)".formatted(
            newSetup.playerCount(), aiNetworks.describe(newSetup.playerCount()), e.getMessage()));
        scheduleBotMove();
        return;
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
    stopped = false;
    setPlaying(true);
    render();
    scheduleBotMove();
  }

  // moves

  /** The mouse button was released on a territory: a short press does the default action, and a long press shows a menu of the variants. */
  void territoryReleased(WorldView.TerritoryRelease release) {
    // between games, the board is as it was left, and can't be played on
    if (idle()) {
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
        .map(move -> "the fortification, since no armies can be moved")
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
    if (match == null || match.isDone() || stopped || match.isHumanTurn()) {
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
    if (match == null || ! playing || stopped || match.isDone() || match.isHumanTurn()) {
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

  /** What is happening, in the bar, and the players in their corners. */
  private void updateStatus() {
    var env = env();
    if (match.isDone()) {
      statusLabel.setText(env.winner() == null
          ? "Draw: the limit of %d turns was reached".formatted(env.maxTurns())
          : "%s won after %d turns".formatted(describe(env.winner()), env.turns()));
    } else {
      statusLabel.setText(stopped
          ? "Stopped after %d turns".formatted(env.turns())
          : "Turn %d · %s · %s".formatted(env.turns() + 1, env.currentPlayer().name(), env.phase().name().toLowerCase()));
    }
    int toPlace = env.risk().currentTurnState() instanceof TurnState.Reinforcing reinforcing ? reinforcing.totalArmies() : 0;
    for (int i = 0; i < seats.size(); i++) {
      var seat = seats.get(i);
      var color = RiskConfig.playerColor(i);
      if (idle()) {
        // between games: who plays the seat in the next game, which can be changed by tapping the card
        var kind = seatKinds[i];
        seat.row().setVisible(true);
        seat.title().setText("Player %d · %s".formatted(i + 1, kind == null ? "Off" : kind.label()));
        seat.icon().getChildren().setAll(Icons.forKind(kind, 18, Icons.DARK));
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
      var playerKind = setup.kinds().get(env.playerIndex(player));
      seat.title().setText("%s · %s".formatted(player.name(), playerKind.label()));
      seat.icon().getChildren().setAll(Icons.forKind(playerKind, 18, Icons.DARK));
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
    updateTransport();
    // what a human can do, and a message about why something didn't work, in the card of the human whose turn it is
    var current = match.isDone() ? null : env().currentPlayer();
    for (int i = 0; i < seats.size(); i++) {
      var seat = seats.get(i);
      boolean here = human && ! idle() && current != null && playerOfSeat(i) == current;
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
