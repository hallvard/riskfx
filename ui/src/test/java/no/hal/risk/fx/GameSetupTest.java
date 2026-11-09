package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.hal.risk.FortifyMode;
import org.junit.jupiter.api.Test;

public class GameSetupTest {

  @Test
  public void testASetupHasTwoToFourPlayers() {
    assertThrows(IllegalArgumentException.class, () -> new GameSetup(List.of()));
    assertThrows(IllegalArgumentException.class, () -> GameSetup.of(PlayerKind.HUMAN));
    assertThrows(IllegalArgumentException.class, () -> GameSetup.of(PlayerKind.AI, PlayerKind.AI, PlayerKind.AI, PlayerKind.AI, PlayerKind.AI));
    assertEquals(2, GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI).playerCount());
    assertEquals(4, GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.AI).playerCount());
  }

  @Test
  public void testQueries() {
    var setup = GameSetup.of(PlayerKind.HUMAN, PlayerKind.GREEDY);
    assertTrue(setup.hasHuman());
    assertFalse(setup.hasAi());
    assertTrue(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY).hasAi());
    assertFalse(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY).hasHuman());
  }

  @Test
  public void testConfigurationOfTheGame() {
    var config = GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.AI).riskConfig(FortifyMode.CONNECTED);
    assertEquals(3, config.playerCount());
    assertEquals(FortifyMode.CONNECTED, config.fortifyMode());
  }

  @Test
  public void testPlayersAreInTheFirstSeatsUnlessSaidOtherwise() {
    var setup = GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY);
    assertEquals(List.of(0, 1, 2), setup.seats());
    assertEquals(2, setup.playerOfSeat(2));
    assertEquals(-1, setup.playerOfSeat(3));
  }

  @Test
  public void testAnySeatCanBeLeftEmpty() {
    // seats 1 and 4 are in the game, which makes two players, and the others are empty
    var setup = GameSetup.ofSeats(new PlayerKind[] {PlayerKind.HUMAN, null, PlayerKind.CAUTIOUS, null});
    assertEquals(List.of(PlayerKind.HUMAN, PlayerKind.CAUTIOUS), setup.kinds());
    assertEquals(List.of(0, 2), setup.seats());
    assertEquals(0, setup.playerOfSeat(0));
    assertEquals(-1, setup.playerOfSeat(1));
    assertEquals(1, setup.playerOfSeat(2));
    assertEquals(2, setup.playerCount());
    // and gives the kind of each seat back
    assertArrayEquals(new PlayerKind[] {PlayerKind.HUMAN, null, PlayerKind.CAUTIOUS, null}, setup.seatKinds());
    assertThrows(IllegalArgumentException.class, () -> GameSetup.ofSeats(new PlayerKind[] {PlayerKind.HUMAN, null, null, null}));
  }

  @Test
  public void testThePlayersHaveTheNamesAndColorsOfTheirSeats() {
    var risk = GameSetup.ofSeats(new PlayerKind[] {null, PlayerKind.AI, null, PlayerKind.GREEDY}).riskConfig(FortifyMode.ADJACENT).createRisk();
    assertEquals(2, risk.players().size());
    assertEquals("Player 2", risk.players().get(0).name());
    assertEquals("Player 4", risk.players().get(1).name());
    assertEquals(no.hal.risk.RiskConfig.playerColor(1), risk.players().get(0).color());
    assertEquals(no.hal.risk.RiskConfig.playerColor(3), risk.players().get(1).color());
  }

  @Test
  public void testSeatsMustBeValid() {
    assertThrows(IllegalArgumentException.class, () -> new GameSetup(List.of(PlayerKind.AI, PlayerKind.AI), List.of(0, 4)));
    assertThrows(IllegalArgumentException.class, () -> new GameSetup(List.of(PlayerKind.AI, PlayerKind.AI), List.of(2, 1)));
    assertThrows(IllegalArgumentException.class, () -> new GameSetup(List.of(PlayerKind.AI, PlayerKind.AI), List.of(1, 1)));
    assertThrows(IllegalArgumentException.class, () -> new GameSetup(List.of(PlayerKind.AI, PlayerKind.AI), List.of(0)));
  }

  @Test
  public void testTheDefaultOfEachSeat() {
    assertEquals(List.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.CAUTIOUS), GameSetup.SEAT_DEFAULTS);
  }

  @Test
  public void testFortificationIsToAdjacentTerritoriesForEveryone() {
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI).fortifyMode());
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.HUMAN, PlayerKind.GREEDY).fortifyMode());
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.HUMAN, PlayerKind.HUMAN).fortifyMode());
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.GREEDY).fortifyMode());
  }

  @Test
  public void testEveryBotKindHasABotOfThatName() {
    for (var kind : PlayerKind.values()) {
      if (kind == PlayerKind.HUMAN || kind == PlayerKind.AI) {
        assertEquals(null, kind.botName());
      } else {
        assertEquals(kind.botName(), no.hal.risk.ai.Bots.byName(kind.botName()).name(), kind.label());
      }
    }
    // the human and the AI come first among the choices, and all the labels are different
    assertEquals(PlayerKind.HUMAN, PlayerKind.values()[0]);
    assertEquals(PlayerKind.AI, PlayerKind.values()[1]);
    assertEquals(PlayerKind.values().length, java.util.Arrays.stream(PlayerKind.values()).map(PlayerKind::label).distinct().count());
  }
}
