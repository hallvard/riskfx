package no.hal.risk.fx;

import javafx.scene.paint.Color;
import no.hal.risk.PlayerColor;

/** Converts the colors of the rules to colors of JavaFX. */
public class FxColors {

  public static Color of(PlayerColor color) {
    return Color.color(color.red(), color.green(), color.blue());
  }
}
