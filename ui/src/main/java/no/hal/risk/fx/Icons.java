package no.hal.risk.fx;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.IkonResolver;

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

  private static final String FONT_FAMILY = "Material Design Icons";
  private static final String FONT_FILE = "/META-INF/resources/materialdesignicons2/5.8.55/fonts/materialdesignicons-webfont.ttf";
  private static final StringBuilder fontReport = new StringBuilder();
  private static boolean fontChecked;

  /**
   * What happened when the font was loaded, for the help text: icons that are boxes with a cross instead mean that the font didn't load,
   * as it didn't in the first native app on Android, and this says where it went wrong.
   */
  public static String fontReport() {
    return fontReport.toString();
  }

  private static void report(String line) {
    fontReport.append(line).append('\n');
    System.err.println("Icon font: " + line);
  }

  /**
   * Ikonli loads the font from a url, which a native app may not be able to do (it was not there on Android), so it is also loaded here
   * from a stream before the first icon is made, which registers it by name, which is what an icon says in its style. Both are reported.
   */
  private static void loadFont() {
    if (fontChecked) {
      return;
    }
    fontChecked = true;
    try {
      var url = Icons.class.getResource(FONT_FILE);
      report("font file on the class path: " + (url != null));
      report("javafx " + System.getProperty("javafx.runtime.version") + ", " + System.getProperty("os.name") + " "
          + System.getProperty("os.arch") + ", " + System.getProperty("java.vm.name") + ", families: " + Font.getFamilies().size());
      byte[] bytes;
      try (var stream = Icons.class.getResourceAsStream(FONT_FILE)) {
        bytes = stream != null ? stream.readAllBytes() : new byte[0];
      }
      report("bytes read: " + bytes.length);
      var font = Font.loadFont(new java.io.ByteArrayInputStream(bytes), 16);
      report("from a stream: " + describe(font));
      if (font == null) {
        // JavaFX copies the font to a temporary file when it is loaded from a stream or a url, and the temporary directory of a phone
        // may not be one the app can write to: another directory is tried, with the font as a file there, and as the temporary directory
        for (var property : new String[] {"java.io.tmpdir", "user.home", "user.dir", "java.home"}) {
          var dir = System.getProperty(property);
          report(property + ": " + dir);
          if (dir == null || font != null) {
            continue;
          }
          try {
            var file = java.nio.file.Files.createTempFile(java.nio.file.Path.of(dir), "icons", ".ttf");
            java.nio.file.Files.write(file, bytes);
            font = Font.loadFont(file.toUri().toString(), 16);
            report("  from a file there: " + describe(font));
            if (font == null && ! property.equals("java.io.tmpdir")) {
              System.setProperty("java.io.tmpdir", dir);
              font = Font.loadFont(new java.io.ByteArrayInputStream(bytes), 16);
              report("  from a stream, with that as the temporary directory: " + describe(font));
            }
          } catch (Exception e) {
            report("  no file there: " + e.getClass().getSimpleName());
          }
        }
      }
      report("the family is known: " + Font.getFamilies().contains(FONT_FAMILY));
    } catch (Throwable e) {
      report("loading failed: " + e);
    }
  }

  private static String describe(Font font) {
    return font != null ? font.getFamily() : "no font";
  }

  /** The icon with the name (as the constants are) at the size, which is the height of it in pixels, and in the color. */
  public static Node of(String name, double size, Paint color) {
    loadFont();
    var icon = new FontIcon(name);
    if (fontReport.indexOf("Ikonli's own load") < 0) {
      try {
        var font = IkonResolver.getInstance().resolve(name).getFont();
        report("Ikonli's own load: " + (font instanceof Font f ? f.getFamily() : "no font"));
      } catch (Throwable e) {
        report("Ikonli's own load failed: " + e);
      }
    }
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
