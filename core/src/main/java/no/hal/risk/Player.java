package no.hal.risk;

public class Player {

  private final String name;
  private final PlayerColor color;

  public Player(String name, PlayerColor color) {
    this.name = name;
    this.color = color;
  }

  public String name() {
    return name;
  }

  public PlayerColor color() {
    return color;
  }
}
