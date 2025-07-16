package no.hal.risk.fx;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

public class LabelListCell<T> extends ListCell<T> {

  private final LabelProvider<T> labelProvider;

  public LabelListCell(LabelProvider<T> labelProvider) {
    this.labelProvider = labelProvider;
  }

  @Override
  protected void updateItem(T item, boolean empty) {
    super.updateItem(item, empty);
    if (empty || item == null) {
      setText(null);
    } else {
      var label = labelProvider.getLabel(item);
      setText(label);
      var color = labelProvider.getColor(item);
      if (color != null) {
        String toString = color.toString().substring(2);
        if (toString.length() > 6) {
          // remove everything besides rrggbb
          toString = toString.substring(0, 6);
        }
        setStyle("-fx-background-color: #%s".formatted(toString));
      }
    }
  }

  public static class Factory<T> implements Callback<ListView<T>, ListCell<T>> {

    private final LabelProvider<T> labelProvider;

    public Factory(LabelProvider<T> labelProvider) {
      this.labelProvider = labelProvider;
    }

    @Override
    public ListCell<T> call(ListView<T> listView) {
      return new LabelListCell<>(labelProvider);
    }
  }
}
