package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import no.hal.risk.Player;
import no.hal.risk.PlayerColor;
import no.hal.risk.RiskConfig;
import org.junit.jupiter.api.Test;

public class PlayerColorsTest {

  @Test
  public void testPlayerColorsAreDistinctAndDifferFromTheMarkingColors() {
    var risk = new RiskConfig(6).createRisk();
    var colors = risk.players().stream().map(Player::color).toList();
    assertEquals(6, new HashSet<>(colors).size());
    // the colors marking the last move
    var marking = List.of(BattleColors.NO_LOSS, BattleColors.ONLY_LOSS, BattleColors.MOST_LOSS, BattleColors.LEAST_LOSS, BattleColors.OTHER_MOVE);
    for (var color : colors) {
      for (var mark : marking) {
        var markColor = new PlayerColor(mark.getRed(), mark.getGreen(), mark.getBlue());
        assertTrue(color.distanceTo(markColor) > 0.35, "%s is too close to %s".formatted(color, mark));
      }
    }
  }

  @Test
  public void testConversionToJavaFx() {
    var color = FxColors.of(PlayerColor.rgb(0xA0724A));
    assertEquals(0xA0 / 255.0, color.getRed(), 1e-6);
    assertEquals(0x72 / 255.0, color.getGreen(), 1e-6);
    assertEquals(0x4A / 255.0, color.getBlue(), 1e-6);
  }
}
