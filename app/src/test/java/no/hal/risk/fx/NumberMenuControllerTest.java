package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class NumberMenuControllerTest {

  @Test
  public void fewOptionsAreAllListed() {
    assertEquals(List.of(), NumberMenuController.options(0));
    assertEquals(List.of(1), NumberMenuController.options(1));
    assertEquals(List.of(1, 2, 3, 4, 5), NumberMenuController.options(5));
  }

  @Test
  public void manyOptionsIncludeOneHalfAndMax() {
    for (int max = 6; max <= 200; max++) {
      var options = NumberMenuController.options(max);
      assertTrue(options.size() <= NumberMenuController.MAX_ENTRIES, "max " + max + ": " + options);
      assertTrue(options.contains(1) && options.contains(max) && options.contains(Math.round(max / 2f)), "max " + max + ": " + options);
      assertEquals(options.stream().sorted().distinct().toList(), options);
    }
    assertEquals(List.of(1, 3, 5, 8, 10), NumberMenuController.options(10));
  }

  @Test
  public void optionsStartingAboveOneAreShifted() {
    assertEquals(List.of(3, 4), NumberMenuController.options(3, 4));
    assertEquals(List.of(3, 5, 7, 10, 12), NumberMenuController.options(3, 12));
    assertEquals(List.of(), NumberMenuController.options(4, 3));
  }
}
