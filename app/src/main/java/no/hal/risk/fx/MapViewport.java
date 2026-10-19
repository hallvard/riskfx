package no.hal.risk.fx;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;

/**
 * Shows a map that can be zoomed and moved. At first it is as large as fits in the room, and then:
 * <ul>
 * <li>Ctrl or Cmd with the scroll wheel, or a pinch on a trackpad, zooms around the mouse</li>
 * <li>the scroll wheel or two fingers on a trackpad moves the map</li>
 * <li>dragging with the right or middle mouse button moves the map</li>
 * <li>{@link #reset()} (the key 0 in the application) fits the map to the room again</li>
 * </ul>
 * Pressing with the left button is left to the map itself, and works as before, since the map is only transformed.
 */
public class MapViewport extends Pane {

  private static final double MAX_FIT_SCALE = 1.8;
  private static final double MIN_ZOOM = 0.7;
  private static final double MAX_ZOOM = 12;
  /** How much a notch of the scroll wheel zooms, in the zoom for each unit of the scroll. */
  private static final double ZOOM_PER_SCROLL_UNIT = 0.0025;

  private final ReadOnlyDoubleWrapper scale = new ReadOnlyDoubleWrapper(1);
  private final Node content;
  private final Affine transform = new Affine();
  /** Whether the map is still as it was fitted, which it is kept as when the window is resized. */
  private boolean fitted = true;
  private Point2D dragStart;

  public MapViewport(Node content) {
    this.content = content;
    getChildren().add(content);
    content.getTransforms().add(transform);
    var clip = new Rectangle();
    clip.widthProperty().bind(widthProperty());
    clip.heightProperty().bind(heightProperty());
    setClip(clip);
    setMinSize(0, 0);
    widthProperty().subscribe(this::refit);
    heightProperty().subscribe(this::refit);
    content.layoutBoundsProperty().subscribe(this::refit);

    addEventFilter(ScrollEvent.SCROLL, event -> {
      if (event.isShortcutDown()) {
        zoom(Math.exp(event.getDeltaY() * ZOOM_PER_SCROLL_UNIT), event.getX(), event.getY());
      } else {
        move(event.getDeltaX(), event.getDeltaY());
      }
      event.consume();
    });
    addEventFilter(ZoomEvent.ZOOM, event -> {
      zoom(event.getZoomFactor(), event.getX(), event.getY());
      event.consume();
    });
    addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
      dragStart = event.getButton() != MouseButton.PRIMARY ? new Point2D(event.getX(), event.getY()) : null;
    });
    addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
      if (dragStart != null) {
        move(event.getX() - dragStart.getX(), event.getY() - dragStart.getY());
        dragStart = new Point2D(event.getX(), event.getY());
        event.consume();
      }
    });
  }

  /** How many pixels a unit of the map is, which text and lines that should keep their size on the screen are divided by. */
  public ReadOnlyDoubleProperty scaleProperty() {
    return scale.getReadOnlyProperty();
  }

  /** The map as large as fits in the room, and in the middle of it. */
  public void reset() {
    fitted = true;
    refit();
  }

  private void refit() {
    var bounds = content.getBoundsInLocal();
    if (! fitted || bounds.getWidth() <= 0 || bounds.getHeight() <= 0 || getWidth() <= 0 || getHeight() <= 0) {
      return;
    }
    double fit = Math.min(MAX_FIT_SCALE, Math.min(getWidth() / bounds.getWidth(), getHeight() / bounds.getHeight()));
    transform.setToIdentity();
    transform.prependScale(fit, fit);
    transform.prependTranslation(
        (getWidth() - bounds.getWidth() * fit) / 2 - bounds.getMinX() * fit,
        (getHeight() - bounds.getHeight() * fit) / 2 - bounds.getMinY() * fit);
    scale.set(fit);
  }

  private double fitScale() {
    var bounds = content.getBoundsInLocal();
    return bounds.getWidth() > 0 && bounds.getHeight() > 0
        ? Math.min(MAX_FIT_SCALE, Math.min(getWidth() / bounds.getWidth(), getHeight() / bounds.getHeight())) : 1;
  }

  /** Zooms in (more than 1) or out around a point of this pane, within the limits. */
  public void zoom(double factor, double x, double y) {
    double current = transform.getMxx();
    double fit = fitScale();
    double limited = Math.max(fit * MIN_ZOOM, Math.min(fit * MAX_ZOOM, current * factor));
    double actual = limited / current;
    if (actual != 1) {
      fitted = false;
      transform.prependScale(actual, actual, x, y);
      scale.set(transform.getMxx());
    }
  }

  private void move(double dx, double dy) {
    fitted = false;
    transform.prependTranslation(dx, dy);
  }
}
