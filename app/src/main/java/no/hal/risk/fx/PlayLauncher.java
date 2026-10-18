package no.hal.risk.fx;

import javafx.application.Application;

/**
 * Starts {@link PlayApp}. A separate class is needed to start a JavaFX application from the class path.
 * Optional arguments: {@code --ai2=file} and {@code --ai4=file} with the trained networks.
 */
public class PlayLauncher {

  public static void main(String[] args) {
    Application.launch(PlayApp.class, args);
  }
}
