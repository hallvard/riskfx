package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MapViewportClampTest {

  @Test
  public void aLargerMapCoversTheRectangle() {
    // the rectangle is 100 to 300
    assertEquals(0, MapViewport.shift(100, 300, 0, 400), 1e-9);      // covers it: stays
    assertEquals(0, MapViewport.shift(100, 300, 100, 300), 1e-9);    // exactly: stays
    assertEquals(-50, MapViewport.shift(100, 300, 150, 600), 1e-9);  // the top is below the top of the rectangle: moved up
    assertEquals(40, MapViewport.shift(100, 300, -300, 260), 1e-9);  // the bottom is above the bottom of it: moved down
    assertEquals(-900, MapViewport.shift(100, 300, 1000, 1500), 1e-9); // gone far off: brought back
  }

  @Test
  public void aSmallerMapIsInsideTheRectangle() {
    assertEquals(0, MapViewport.shift(100, 300, 150, 250), 1e-9);
    assertEquals(50, MapViewport.shift(100, 300, 50, 150), 1e-9);    // sticks out above: moved down
    assertEquals(-60, MapViewport.shift(100, 300, 210, 360), 1e-9);  // sticks out below: moved up
  }
}
