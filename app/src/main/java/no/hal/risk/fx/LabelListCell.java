package no.hal.risk.fx;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import javafx.util.StringConverter;

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
      setStyle(null);
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
      } else {
        setStyle(null);
      }
    }
  }

  /**
   * Configures a ComboBox to use the LabelProvider for both the button cell and the popup cells.
   * The converter is needed since ComboBoxListViewSkin may set the button cell's text
   * from the converter (default: toString) after updateItem has run.
   */
  public static <T> void configure(ComboBox<T> comboBox, LabelProvider<T> labelProvider) {
    comboBox.setConverter(new StringConverter<>() {
      @Override
      public String toString(T item) {
        return item != null ? labelProvider.getLabel(item) : null;
      }

      @Override
      public T fromString(String string) {
        throw new UnsupportedOperationException();
      }
    });
    comboBox.setButtonCell(new LabelListCell<>(labelProvider));
    comboBox.setCellFactory(new Factory<>(labelProvider));
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
