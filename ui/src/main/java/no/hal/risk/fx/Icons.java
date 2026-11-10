package no.hal.risk.fx;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Small icons. Most are glyphs of an icon font (Ikonli, with the Material Design Icons 2 set), which is a file in the application
 * that is drawn the same everywhere, unlike a symbol written as a character, which is drawn by a font of the device, and shows
 * nothing on one that has no font with it (a phone running the native app, for instance). The font is also loaded by the native
 * application, see the resources list in the mobile module.
 * <p>
 * The icons are given by name, see the constants, which are the names the font has: <a href="https://pictogrammers.com/library/mdi/">
 * pictogrammers.com/library/mdi</a>, with the prefix mdi2 and the first letter of the name, as in {@code mdi2p-play}.
 * The two icons {@link #networkDrawn} and {@link #robotDrawn} are drawn as shapes instead, and are kept to be used again.
 */
public final class Icons {

  private Icons() {
  }

  public static final String SETTINGS = "mdi2c-cog";
  public static final String PLAY = "mdi2p-play";
  public static final String PAUSE = "mdi2p-pause";
  public static final String STOP = "mdi2s-stop";
  public static final String SKIP = "mdi2s-skip-next";
  public static final String HELP = "mdi2h-help-circle-outline";
  public static final String HUMAN = "mdi2a-account";
  /** The AI, which is a neural network, is shown as a brain. */
  public static final String AI = "mdi2b-brain";
  public static final String BOT = "mdi2r-robot";
  public static final String OFF = "mdi2b-block-helper";

  /** The icon with the name (as the constants are) at the size, which is the height of it in pixels, and in the color. */
  public static Node of(String name, double size, Paint color) {
    var icon = new FontIcon(name);
    icon.setIconSize((int) Math.round(size));
    icon.setIconColor(color);
    return icon;
  }

  /**
   * The icon of what controls a player: a person for a human, a brain for the AI, a robot for a bot that follows rules,
   * and a crossed circle for no player, which is null.
   */
  public static Node forKind(PlayerKind kind, double size, Paint color) {
    if (kind == null) {
      return of(OFF, size, color);
    }
    return switch (kind) {
      case HUMAN -> of(HUMAN, size, color);
      case AI -> of(AI, size, color);
      default -> of(BOT, size, color);
    };
  }

  /**
   * A small neural network, drawn as shapes (the AI used to have it, and {@link #AI} is used now):  three layers of three dots, with each dot joined to all the dots of the next layer.
   */
  public static Node networkDrawn(double size, Paint color) {
    double[] columns = {0.12, 0.5, 0.88};
    double[] rows = {0.14, 0.5, 0.86};
    var group = new Group();
    // the lines are first, so the dots are on top of them
    for (int layer = 0; layer + 1 < columns.length; layer++) {
      for (double from : rows) {
        for (double to : rows) {
          var line = new Line(columns[layer] * size, from * size, columns[layer + 1] * size, to * size);
          line.setStroke(color);
          // lighter than the dots, so that the links don't run together into a dark patch where the icon is small
          line.setOpacity(0.55);
          line.setStrokeWidth(Math.max(0.6, size * 0.03));
          group.getChildren().add(line);
        }
      }
    }
    for (double column : columns) {
      for (double row : rows) {
        var dot = new Circle(column * size, row * size, size * 0.115);
        dot.setFill(color);
        group.getChildren().add(dot);
      }
    }
    return group;
  }

  /** A robot's head, drawn as shapes (a bot used to have it, and {@link #BOT} is used now): a square head with two eyes and a mouth, and an antenna. */
  public static Node robotDrawn(double size, Paint color) {
    var head = new Rectangle(size * 0.12, size * 0.3, size * 0.76, size * 0.62);
    head.setArcWidth(size * 0.16);
    head.setArcHeight(size * 0.16);
    head.setFill(null);
    head.setStroke(color);
    head.setStrokeWidth(size * 0.09);
    var leftEye = new Circle(size * 0.34, size * 0.52, size * 0.08);
    var rightEye = new Circle(size * 0.66, size * 0.52, size * 0.08);
    leftEye.setFill(color);
    rightEye.setFill(color);
    var mouth = new Line(size * 0.34, size * 0.74, size * 0.66, size * 0.74);
    mouth.setStroke(color);
    mouth.setStrokeWidth(size * 0.07);
    var antenna = new Line(size * 0.5, size * 0.3, size * 0.5, size * 0.1);
    antenna.setStroke(color);
    antenna.setStrokeWidth(size * 0.07);
    var tip = new Circle(size * 0.5, size * 0.08, size * 0.07);
    tip.setFill(color);
    return new Group(head, leftEye, rightEye, mouth, antenna, tip);
  }

  /** Default color for an icon on a light background. */
  public static final Paint DARK = Color.rgb(50, 50, 50);
}
