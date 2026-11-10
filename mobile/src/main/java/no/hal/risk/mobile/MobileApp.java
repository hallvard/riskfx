package no.hal.risk.mobile;

import java.util.Map;
import javafx.application.Application;
import javafx.stage.Stage;
import no.hal.risk.fx.PlayApp;
import no.hal.risk.fx.Settings;

/**
 * Starts the user interface as a native app, on Android, built with Gluon (GraalVM native image), and on the desktop,
 * with {@code mvn -Pmobile -pl mobile gluonfx:run}. The window is the whole screen, and follows it when it is turned.
 */
public class MobileApp extends Application {

  @Override
  public void start(Stage stage) {
    // the settings are kept in the home directory, which on Android is the private directory of the app
    new PlayApp().show(stage, Map.of("settings", Settings.defaultFile().toString()), false, true);
  }

  public static void main(String[] args) {
    launch(args);
  }
}
