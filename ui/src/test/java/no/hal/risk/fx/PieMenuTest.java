package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PieMenuTest {

  private static int part(double dx, double dy, int choices) {
    return PieMenu.partAt(dx, dy, choices, 40, 100);
  }

  @Test
  public void theMiddleIsTheDefaultAndOutsideIsNothing() {
    assertEquals(0, part(0, 0, 5));
    assertEquals(0, part(20, -20, 5));
    assertEquals(-1, part(0, -150, 5));
    assertEquals(-1, part(101, 0, 5));
  }

  @Test
  public void theSectorsAreNumberedClockwiseFromTheTop() {
    // four sectors of the ring, each a quarter of the circle, with the first one starting at the top
    assertEquals(1, part(10, -70, 5));   // up and a little to the right
    assertEquals(2, part(70, 10, 5));    // right
    assertEquals(3, part(-10, 70, 5));   // down
    assertEquals(4, part(-70, -10, 5));  // left
    // just left of the top is the last sector, since the first starts at the top
    assertEquals(4, part(-1, -70, 5));
  }

  @Test
  public void aLoneDefaultIsAllThereIs() {
    assertEquals(0, part(0, 0, 1));
    assertEquals(-1, part(0, -70, 1));
  }

  @Test
  public void twoChoicesGiveOneSectorAroundTheWholeRing() {
    assertEquals(1, part(0, -70, 2));
    assertEquals(1, part(0, 70, 2));
    assertEquals(1, part(-70, 0, 2));
  }

  @Test
  public void theSectorsCoverAWholeTurnWithoutGaps() {
    for (int choices = 2; choices <= 8; choices++) {
      double end = 0;
      for (int sector = 1; sector < choices; sector++) {
        var angles = PieMenu.sectorAngles(sector, choices);
        assertEquals(end, angles[0], 1e-9);
        end = angles[1];
      }
      assertEquals(2 * Math.PI, end, 1e-9);
    }
    assertArrayEquals(new double[] {0, Math.PI}, PieMenu.sectorAngles(1, 3), 1e-9);
  }
}
