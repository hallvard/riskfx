package no.hal.risk.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

/**
 * Lets the player choose in a menu where the mouse is, with the default under the mouse.
 * The menu doesn't consume the click that closes it.
 * This is the only class that knows what the menu looks like, so it can be replaced with a pie menu
 * centered on the click, with the default in the middle.
 */
public class NumberMenuController {

  /** The most entries in the menu. */
  public static final int MAX_ENTRIES = 5;

  private ContextMenu menu;

  /**
   * The numbers to choose between, in increasing order, from 1 to {@code max}: all of them if there are at most {@link #MAX_ENTRIES},
   * otherwise spread out, always including 1, half and max.
   */
  public static List<Integer> options(int max) {
    var numbers = new TreeSet<Integer>();
    if (max <= MAX_ENTRIES) {
      for (int i = 1; i <= max; i++) {
        numbers.add(i);
      }
    } else {
      numbers.add(1);
      numbers.add(Math.round(max / 4f));
      numbers.add(Math.round(max / 2f));
      numbers.add(Math.round(3 * max / 4f));
      numbers.add(max);
    }
    return List.copyOf(numbers);
  }

  /** The same for the numbers from {@code min} to {@code max}, as if counting from 1. */
  public static List<Integer> options(int min, int max) {
    return options(max - min + 1).stream().map(number -> number + min - 1).toList();
  }

  /** An entry of the menu: what it says, and what to do when it is chosen. */
  public record Choice(String label, Runnable action) {
  }

  /**
   * Shows a menu with the numbers up to {@code max}, replacing any menu that is showing.
   * The preferred number comes first, since that is the default and is under the mouse, then the others in decreasing order.
   *
   * @param owner the node the mouse was over
   * @param screenX where the mouse was, in screen coordinates
   * @param screenY where the mouse was, in screen coordinates
   * @param min the fewest armies that can be chosen
   * @param max the most armies that can be chosen, nothing is shown if it is less than {@code min}
   * @param preferred the default number
   * @param label what an entry says, given the number
   * @param onChosen called with the chosen number
   */
  public void show(Node owner, double screenX, double screenY, int min, int max, int preferred, IntFunction<String> label, IntConsumer onChosen) {
    show(owner, screenX, screenY, min, max, preferred, label, onChosen, List.of());
  }

  /** Like the above, with more choices after the numbers. */
  public void show(Node owner, double screenX, double screenY, int min, int max, int preferred, IntFunction<String> label, IntConsumer onChosen,
      List<Choice> after) {
    var numbers = new ArrayList<>(options(min, max));
    numbers.sort(Comparator.reverseOrder());
    if (numbers.remove((Integer) preferred)) {
      numbers.addFirst(preferred);
    }
    var choices = new ArrayList<>(numbers.stream()
        .map(number -> new Choice(label.apply(number), () -> onChosen.accept(number)))
        .toList());
    choices.addAll(after);
    show(owner, screenX, screenY, choices);
  }

  /**
   * Shows a menu with the choices, replacing any menu that is showing.
   * The first choice is the default: it is emphasized and placed under the mouse, so releasing the button again there chooses it.
   */
  public void show(Node owner, double screenX, double screenY, List<Choice> choices) {
    hide();
    if (choices.isEmpty() || owner.getScene() == null) {
      return;
    }
    var newMenu = new ContextMenu();
    newMenu.setConsumeAutoHidingEvents(false);
    for (int i = 0; i < choices.size(); i++) {
      var choice = choices.get(i);
      var item = new MenuItem(choice.label());
      if (i == 0) {
        item.setStyle("-fx-font-weight: bold;");
      }
      item.setOnAction(_ -> choice.action().run());
      newMenu.getItems().add(item);
    }
    // the menu is placed with its corner at the mouse, and then moved so that the first entry is under it
    newMenu.setOnShown(_ -> {
      var skin = newMenu.getSkin();
      var first = skin != null ? skin.getNode().lookup(".menu-item") : null;
      if (first != null) {
        var bounds = first.localToScreen(first.getBoundsInLocal());
        if (bounds != null) {
          newMenu.setX(newMenu.getX() + screenX - 16 - bounds.getMinX());
          newMenu.setY(newMenu.getY() + screenY - (bounds.getMinY() + bounds.getMaxY()) / 2);
        }
      }
    });
    menu = newMenu;
    newMenu.show(owner, screenX, screenY);
  }

  public boolean isShowing() {
    return menu != null && menu.isShowing();
  }

  public void hide() {
    if (menu != null) {
      menu.hide();
      menu = null;
    }
  }
}
