package no.hal.risk.fx;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
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

  /** Default color for an icon on a light background. */
  public static final Paint DARK = Color.rgb(50, 50, 50);
}
