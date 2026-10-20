package no.hal.risk.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javafx.scene.Node;

/**
 * Lets the player choose in a pie menu centered where the mouse is, with the default in the middle, under the mouse.
 * The menu doesn't consume the click that closes it. The menu itself is {@link PieMenu}, which is the only class that knows
 * what it looks like.
 */
public class NumberMenuController {

  /** The most entries in the menu. */
  public static final int MAX_ENTRIES = 5;

  private PieMenu menu;

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
    var choices = new ArrayList<>(numberChoices(min, max, preferred, label, onChosen));
    choices.addAll(after);
    show(owner, screenX, screenY, choices);
  }

  /** The choices of the numbers from {@code min} to {@code max}: the preferred number first, and then the others in decreasing order. */
  public static List<Choice> numberChoices(int min, int max, int preferred, IntFunction<String> label, IntConsumer onChosen) {
    var numbers = new ArrayList<>(options(min, max));
    numbers.sort(Comparator.reverseOrder());
    if (numbers.remove((Integer) preferred)) {
      numbers.addFirst(preferred);
    }
    return numbers.stream().map(number -> new Choice(label.apply(number), () -> onChosen.accept(number))).toList();
  }

  /**
   * Shows a pie menu with the choices, replacing any menu that is showing. It is centered on the point, and the first choice is
   * the default, in the circle in the middle, where the mouse is, so a click without moving the mouse chooses it.
   * The others are sectors around it, clockwise from the top.
   */
  public void show(Node owner, double screenX, double screenY, List<Choice> choices) {
    hide();
    if (choices.isEmpty() || owner.getScene() == null) {
      return;
    }
    menu = new PieMenu(choices);
    menu.show(owner, screenX, screenY);
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
