package no.hal.risk.fx;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.ClosePath;

/**
 * Small icons, drawn as shapes. A symbol written as a character, such as a cog, is drawn by the font, and a device without a font that
 * has it (a phone running the native app, for instance) shows nothing, whereas a shape looks the same everywhere.
 */
public final class Icons {

  private Icons() {
  }

  /** A cog, for settings: a ring of teeth with a hole in the middle. */
  public static Node gear(double size, Paint color) {
    int teeth = 8;
    double outer = size / 2, inner = outer * 0.72, hole = outer * 0.32;
    var path = new Path();
    path.setFillRule(FillRule.EVEN_ODD);
    // the outline goes around, with each tooth as a flat top between two slanting sides
    double step = 2 * Math.PI / teeth;
    for (int tooth = 0; tooth < teeth; tooth++) {
      double middle = tooth * step;
      double[][] corners = {{inner, middle - step * 0.36}, {outer, middle - step * 0.2}, {outer, middle + step * 0.2}, {inner, middle + step * 0.36}};
      for (int i = 0; i < corners.length; i++) {
        double x = outer + corners[i][0] * Math.sin(corners[i][1]), y = outer - corners[i][0] * Math.cos(corners[i][1]);
        path.getElements().add(tooth == 0 && i == 0 ? new MoveTo(x, y) : new LineTo(x, y));
      }
    }
    path.getElements().add(new ClosePath());
    // the hole, as a polygon, since the fill rule leaves out what is inside both
    int sides = 16;
    for (int i = 0; i < sides; i++) {
      double angle = i * 2 * Math.PI / sides;
      double x = outer + hole * Math.sin(angle), y = outer - hole * Math.cos(angle);
      path.getElements().add(i == 0 ? new MoveTo(x, y) : new LineTo(x, y));
    }
    path.getElements().add(new ClosePath());
    path.setFill(color);
    path.setStroke(null);
    return new Group(path);
  }

  /** Skip, as the button for the next track in a music player: a triangle pointing right, and a bar after it. */
  public static Node skip(double size, Paint color) {
    var triangle = new Polygon(0, 0, size * 0.72, size / 2, 0, size);
    var bar = new Rectangle(size * 0.82, 0, size * 0.18, size);
    triangle.setFill(color);
    bar.setFill(color);
    return new Group(triangle, bar);
  }

  /** Play: a triangle pointing right. */
  public static Node play(double size, Paint color) {
    var triangle = new Polygon(size * 0.1, 0, size * 0.95, size / 2, size * 0.1, size);
    triangle.setFill(color);
    return new Group(triangle);
  }

  /** Pause: two bars. */
  public static Node pause(double size, Paint color) {
    var left = new Rectangle(size * 0.12, 0, size * 0.28, size);
    var right = new Rectangle(size * 0.6, 0, size * 0.28, size);
    left.setFill(color);
    right.setFill(color);
    return new Group(left, right);
  }

  /** Stop: a square. */
  public static Node stop(double size, Paint color) {
    var square = new Rectangle(size * 0.05, size * 0.05, size * 0.9, size * 0.9);
    square.setFill(color);
    return new Group(square);
  }

  /** A check mark, for accepting. */
  public static Node check(double size, Paint color) {
    var mark = new Polyline(0, size * 0.55, size * 0.38, size * 0.95, size, size * 0.05);
    mark.setStroke(color);
    mark.setStrokeWidth(size * 0.16);
    mark.setStrokeLineCap(StrokeLineCap.ROUND);
    mark.setStrokeLineJoin(StrokeLineJoin.ROUND);
    mark.setFill(null);
    return new Group(mark);
  }

  /** A cross, for cancelling. */
  public static Node cross(double size, Paint color) {
    var first = new Line(0, 0, size, size);
    var second = new Line(0, size, size, 0);
    for (var line : new Line[] {first, second}) {
      line.setStroke(color);
      line.setStrokeWidth(size * 0.16);
      line.setStrokeLineCap(StrokeLineCap.ROUND);
    }
    return new Group(first, second);
  }

  /** A person, for a human player: a head, and shoulders under it. */
  public static Node person(double size, Paint color) {
    var head = new Circle(size * 0.5, size * 0.28, size * 0.22);
    var shoulders = new Path(
        new MoveTo(size * 0.1, size),
        new ArcTo(size * 0.4, size * 0.42, 0, size * 0.9, size, false, true),
        new ClosePath());
    head.setFill(color);
    shoulders.setFill(color);
    shoulders.setStroke(null);
    return new Group(head, shoulders);
  }

  /**
   * A small neural network, for the AI, which is one: three layers of three dots, with each dot joined to all the dots of the next layer.
   */
  public static Node network(double size, Paint color) {
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

  /** A robot's head, for a bot that follows rules: a square head with two eyes and a mouth, and an antenna. */
  public static Node robot(double size, Paint color) {
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

  /** A circle with a slash through it, for a seat that is not taken. */
  public static Node off(double size, Paint color) {
    var ring = new Circle(size * 0.5, size * 0.5, size * 0.42);
    ring.setFill(null);
    ring.setStroke(color);
    ring.setStrokeWidth(size * 0.09);
    var slash = new Line(size * 0.2, size * 0.8, size * 0.8, size * 0.2);
    slash.setStroke(color);
    slash.setStrokeWidth(size * 0.09);
    return new Group(ring, slash);
  }

  /**
   * The icon of what controls a player: a person for a human, a network for the AI, a robot for a bot that follows rules,
   * and a crossed circle for no player, which is null.
   */
  public static Node forKind(PlayerKind kind, double size, Paint color) {
    if (kind == null) {
      return off(size, color);
    }
    return switch (kind) {
      case HUMAN -> person(size, color);
      case AI -> network(size, color);
      default -> robot(size, color);
    };
  }

  /** Default color for an icon on a light background. */
  public static final Paint DARK = Color.rgb(50, 50, 50);
}
