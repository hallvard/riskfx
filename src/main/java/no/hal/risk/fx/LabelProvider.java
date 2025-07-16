package no.hal.risk.fx;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public interface LabelProvider<T> {
    String getLabel(T item);

    default Image getImage(T item) {
        return null;
    }

    default Color getColor(T item) {
        return null;
    }

    default String getTooltip(T item) {
        return null;
    }
}
