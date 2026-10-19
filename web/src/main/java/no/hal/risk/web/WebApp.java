package no.hal.risk.web;

import java.util.Map;
import javafx.application.Application;
import javafx.stage.Stage;
import no.hal.risk.fx.PlayApp;

/**
 * Starts the user interface of the app for each user who opens the page, when run by JPro, which runs the program on a server and shows
 * the window in the browser. Closing the window of one user must not end the program for the others, so it only ends that user's game.
 */
public class WebApp extends Application {

  @Override
  public void start(Stage stage) {
    new PlayApp().show(stage, Map.of(), false);
  }

  /** For running it as an ordinary desktop program too, with {@code mvn -Pweb -pl web javafx:run} or from an IDE. */
  public static void main(String[] args) {
    launch(args);
  }
}
