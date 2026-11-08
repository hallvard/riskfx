package no.hal.risk.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import no.hal.risk.FortifyMode;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.Rules;

/**
 * Which players take part in a game, and what controls each of them. The players sit at up to {@value #MAX_PLAYERS} seats, which any
 * of can be left empty, and the order of the turns is the order of the seats.
 *
 * @param kinds what controls each player, in the order of the turns
 * @param seats the seat of each player, from 0, in the same order, with the seats in increasing order
 */
public record GameSetup(List<PlayerKind> kinds, List<Integer> seats) {

  public static final int MIN_PLAYERS = 2;
  public static final int MAX_PLAYERS = 4;

  /** What controls the player of each seat, to start with, when a seat is taken. */
  public static final List<PlayerKind> SEAT_DEFAULTS = List.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.CAUTIOUS);

  public GameSetup {
    if (kinds.size() < MIN_PLAYERS || kinds.size() > MAX_PLAYERS) {
      throw new IllegalArgumentException("A game has %d to %d players, not %d".formatted(MIN_PLAYERS, MAX_PLAYERS, kinds.size()));
    }
    if (seats == null) {
      seats = IntStream.range(0, kinds.size()).boxed().toList();
    }
    if (seats.size() != kinds.size() || seats.stream().anyMatch(seat -> seat < 0 || seat >= MAX_PLAYERS)
        || ! seats.equals(seats.stream().sorted().distinct().toList())) {
      throw new IllegalArgumentException("Need a seat for each player, in increasing order, from 0 to %d: %s".formatted(MAX_PLAYERS - 1, seats));
    }
    kinds = List.copyOf(kinds);
    seats = List.copyOf(seats);
  }

  /** The players in the first seats. */
  public GameSetup(List<PlayerKind> kinds) {
    this(kinds, null);
  }

  public static GameSetup of(PlayerKind... kinds) {
    return new GameSetup(List.of(kinds));
  }

  /** The seats that are taken have the kind of the player in it, and the others, which are empty, have null. */
  public static GameSetup ofSeats(PlayerKind[] seatKinds) {
    var kinds = new ArrayList<PlayerKind>();
    var seats = new ArrayList<Integer>();
    for (int seat = 0; seat < seatKinds.length; seat++) {
      if (seatKinds[seat] != null) {
        kinds.add(seatKinds[seat]);
        seats.add(seat);
      }
    }
    return new GameSetup(kinds, seats);
  }

  /** The kind of each seat, which is null if it is empty. */
  public PlayerKind[] seatKinds() {
    var seatKinds = new PlayerKind[MAX_PLAYERS];
    for (int player = 0; player < kinds.size(); player++) {
      seatKinds[seats.get(player)] = kinds.get(player);
    }
    return seatKinds;
  }

  /** The number of the player in the seat, in the order of the turns, or -1 if the seat is empty. */
  public int playerOfSeat(int seat) {
    return seats.indexOf(seat);
  }

  public int playerCount() {
    return kinds.size();
  }

  public boolean hasHuman() {
    return kinds.contains(PlayerKind.HUMAN);
  }

  public boolean hasAi() {
    return kinds.contains(PlayerKind.AI);
  }

  /**
   * The rule for fortification: armies are moved to adjacent territories only, for all players.
   * The AI was trained for that, so it is also what makes it fair for a human playing against it.
   */
  public FortifyMode fortifyMode() {
    return FortifyMode.ADJACENT;
  }

  /** The configuration of the game itself. */
  public RiskConfig riskConfig(FortifyMode fortifyMode) {
    return riskConfig(fortifyMode, Rules.DEFAULT);
  }

  public RiskConfig riskConfig(FortifyMode fortifyMode, Rules rules) {
    return riskConfig(fortifyMode, rules, GameMap.CLASSIC);
  }

  public RiskConfig riskConfig(FortifyMode fortifyMode, Rules rules, GameMap map) {
    return new RiskConfig(kinds.size()).withSeats(seats).withMap(map).withFortifyMode(fortifyMode).withRules(rules);
  }
}
