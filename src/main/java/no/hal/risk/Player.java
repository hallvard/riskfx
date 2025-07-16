package no.hal.risk;

import javafx.scene.paint.Color;

public class Player {
    
  private final String name;
  private final Color color;

  public Player(String name, Color color) {
      this.name = name;
      this.color = color;
  }

  public Player(String name, String colorName) {
      this.name = name;
      this.color = Color.valueOf(colorName.toUpperCase());
  }

  public String name() {
      return name;
  }

  public Color color() {
      return color;
  }
}
