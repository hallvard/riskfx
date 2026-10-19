package no.hal.risk.fx;

import java.util.List;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.stage.Screen;

/**
 * A pie menu centered on a point of the screen: the first choice, which is the default, is in a circle in the middle,
 * and the others are sectors of a ring around it, clockwise from the top. A click in the middle, which is where the mouse is when the
 * menu opens, chooses the default, and a click in a sector chooses that one. A click outside, or Escape, closes the menu.
 */
public class PieMenu {

  static final double INNER_RADIUS = 28;
  static final double OUTER_RADIUS = 112;
  private static final double GAP = 2;
  private static final double TEXT_WIDTH = 74;

  private static final Color RING = Color.rgb(250, 250, 250, 0.96);
  private static final Color RING_HOVER = Color.rgb(196, 222, 255, 0.98);
  private static final Color DEFAULT = Color.rgb(232, 240, 255, 0.98);
  private static final Color DEFAULT_HOVER = Color.rgb(176, 208, 255, 0.98);
  private static final Color LINE = Color.rgb(70, 70, 70);

  /** The radius of the circle in the middle when it is alone, so it is easy to hit. */
  private static final double ALONE_RADIUS = 62;

  private final Popup popup = new Popup();
  private final List<NumberMenuController.Choice> choices;
  private final double centerRadius;
  private final Circle middle;
  private final Pane root;
  /** The sectors of the ring, from 1, and nothing at 0, which is the circle in the middle. */
  private final Path[] sectors;
  private int hovered = -1;

  /**
   * Which part of a pie menu a point is in, given as the distance from its center.
   *
   * @return -1 outside the menu, 0 in the circle in the middle, and otherwise the number of the sector in the ring, counted
   *   clockwise from the top, from 1 to {@code choices - 1}
   */
  static int partAt(double dx, double dy, int choices, double innerRadius, double outerRadius) {
    double distance = Math.hypot(dx, dy);
    if (distance <= innerRadius) {
      return 0;
    }
    if (choices == 1 || distance > outerRadius) {
      return -1;
    }
    // the angle clockwise from the top, from 0 to a whole turn
    double angle = Math.atan2(dx, -dy);
    if (angle < 0) {
      angle += 2 * Math.PI;
    }
    return 1 + Math.min(choices - 2, (int) (angle / (2 * Math.PI / (choices - 1))));
  }

  /** The angles of the sector of the ring with the number (from 1), as the start and the end, in radians clockwise from the top. */
  static double[] sectorAngles(int sector, int choices) {
    double width = 2 * Math.PI / (choices - 1);
    return new double[] {(sector - 1) * width, sector * width};
  }

  private static Point2D onCircle(double radius, double angle) {
    return new Point2D(OUTER_RADIUS + radius * Math.sin(angle), OUTER_RADIUS - radius * Math.cos(angle));
  }

  /** @param choices at least one, of which the first is the default, which is in the middle */
  public PieMenu(List<NumberMenuController.Choice> choices) {
    this.choices = List.copyOf(choices);
    this.centerRadius = choices.size() == 1 ? ALONE_RADIUS : INNER_RADIUS;
    root = new Pane();
    double size = 2 * OUTER_RADIUS;
    root.setPrefSize(size, size);
    root.setMinSize(size, size);
    root.setMaxSize(size, size);
    sectors = new Path[choices.size()];
    for (int sector = 1; sector < choices.size(); sector++) {
      sectors[sector] = sector(sector, choices.size());
      root.getChildren().add(sectors[sector]);
    }
    middle = new Circle(OUTER_RADIUS, OUTER_RADIUS, centerRadius);
    middle.setStroke(LINE);
    middle.setStrokeWidth(1.5);
    middle.setMouseTransparent(true);
    root.getChildren().add(middle);
    for (int part = 0; part < choices.size(); part++) {
      root.getChildren().add(label(part));
    }
    paint();

    root.setOnMouseMoved(event -> hover(partOf(event, root)));
    root.setOnMouseExited(_ -> hover(-1));
    root.setOnMouseClicked(event -> {
      int part = partOf(event, root);
      if (event.getButton() == MouseButton.PRIMARY && part >= 0) {
        popup.hide();
        this.choices.get(part).action().run();
      }
    });
    popup.getContent().add(root);
    popup.setAutoHide(true);
    popup.setConsumeAutoHidingEvents(false);
    popup.setHideOnEscape(true);
  }

  private int partOf(MouseEvent event, Pane root) {
    return partAt(event.getX() - OUTER_RADIUS, event.getY() - OUTER_RADIUS, choices.size(), centerRadius, OUTER_RADIUS);
  }

  private void hover(int part) {
    if (part != hovered) {
      hovered = part;
      paint();
    }
  }

  /** The sector of the ring with the number, which is a ring between two radii, from one angle to another, with small gaps. */
  private static Path sector(int sector, int choices) {
    if (choices == 2) {
      return ring();
    }
    var angles = sectorAngles(sector, choices);
    double gap = GAP / OUTER_RADIUS;
    double start = angles[0] + gap, end = angles[1] - gap;
    var path = new Path();
    var outerStart = onCircle(OUTER_RADIUS - 1, start);
    var outerEnd = onCircle(OUTER_RADIUS - 1, end);
    var innerEnd = onCircle(INNER_RADIUS + GAP, end);
    var innerStart = onCircle(INNER_RADIUS + GAP, start);
    boolean large = end - start > Math.PI;
    path.getElements().addAll(
        new MoveTo(outerStart.getX(), outerStart.getY()),
        new ArcTo(OUTER_RADIUS - 1, OUTER_RADIUS - 1, 0, outerEnd.getX(), outerEnd.getY(), large, true),
        new LineTo(innerEnd.getX(), innerEnd.getY()),
        new ArcTo(INNER_RADIUS + GAP, INNER_RADIUS + GAP, 0, innerStart.getX(), innerStart.getY(), large, false),
        new ClosePath());
    path.setStroke(LINE);
    path.setStrokeWidth(1);
    path.setMouseTransparent(true);
    return path;
  }

  /** The whole ring, as the only sector, so without the gaps that separate sectors: two circles, with the inner one cut out. */
  private static Path ring() {
    var path = new Path();
    path.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
    for (double radius : new double[] {OUTER_RADIUS - 1, INNER_RADIUS + GAP}) {
      path.getElements().addAll(
          new MoveTo(OUTER_RADIUS, OUTER_RADIUS - radius),
          new ArcTo(radius, radius, 0, OUTER_RADIUS, OUTER_RADIUS + radius, false, true),
          new ArcTo(radius, radius, 0, OUTER_RADIUS, OUTER_RADIUS - radius, false, true),
          new ClosePath());
    }
    path.setStroke(LINE);
    path.setStrokeWidth(1);
    path.setMouseTransparent(true);
    return path;
  }

  /** The text of a part, in the middle of the circle or of the sector. */
  private Node label(int part) {
    var text = new Text(choices.get(part).label());
    text.setFont(Font.font(null, part == 0 ? FontWeight.BOLD : FontWeight.NORMAL, 11.5));
    // in a small circle in the middle, the text is wrapped to fit it
    text.setWrappingWidth(part == 0 && choices.size() > 1 ? 2 * INNER_RADIUS - 6 : TEXT_WIDTH);
    text.setTextAlignment(TextAlignment.CENTER);
    text.setMouseTransparent(true);
    Point2D center = new Point2D(OUTER_RADIUS, OUTER_RADIUS);
    if (part > 0) {
      var angles = sectorAngles(part, choices.size());
      // a ring of one sector is all around, so its text is at the top, where it is read first
      double angle = choices.size() == 2 ? 0 : (angles[0] + angles[1]) / 2;
      center = onCircle((INNER_RADIUS + OUTER_RADIUS) / 2, angle);
    }
    var bounds = text.getLayoutBounds();
    text.setLayoutX(center.getX() - bounds.getMinX() - bounds.getWidth() / 2);
    text.setLayoutY(center.getY() - bounds.getMinY() - bounds.getHeight() / 2);
    return text;
  }

  private void paint() {
    middle.setFill(hovered == 0 ? DEFAULT_HOVER : DEFAULT);
    for (int part = 1; part < sectors.length; part++) {
      sectors[part].setFill(hovered == part ? RING_HOVER : RING);
    }
  }

  /** Shows the menu with its center at a point of the screen, moved only as much as needed to be on the screen. */
  public void show(Node owner, double screenX, double screenY) {
    var screens = Screen.getScreensForRectangle(screenX, screenY, 1, 1);
    var bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
    double size = 2 * OUTER_RADIUS;
    double x = Math.max(bounds.getMinX(), Math.min(bounds.getMaxX() - size, screenX - OUTER_RADIUS));
    double y = Math.max(bounds.getMinY(), Math.min(bounds.getMaxY() - size, screenY - OUTER_RADIUS));
    popup.show(owner, x, y);
  }

  /** The content of the menu, for rendering it without showing it. */
  Node content() {
    return root;
  }

  public boolean isShowing() {
    return popup.isShowing();
  }

  public void hide() {
    popup.hide();
  }
}
