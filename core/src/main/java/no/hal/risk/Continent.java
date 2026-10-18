package no.hal.risk;

import java.util.ArrayList;
import java.util.List;

public record Continent(String name, int bonusPoints, List<Territory> territories) {
  public Continent {
    territories = new ArrayList<>(territories);
  }
  public Continent(String name, int bonusPoints, Territory... territories) {
    this(name, bonusPoints, List.of(territories));
  }
}
