package no.hal.risk;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record World(List<Continent> continents, List<Territory> allTerritories, Map<Territory, Set<Territory>> borders) {

  public World {
    continents = List.copyOf(continents);
    allTerritories = List.copyOf(allTerritories);
    // order the neighbours as allTerritories, so iteration order is deterministic
    var indices = new HashMap<Territory, Integer>();
    for (int i = 0; i < allTerritories.size(); i++) {
      indices.put(allTerritories.get(i), i);
    }
    var orderedBorders = new HashMap<Territory, Set<Territory>>();
    for (var entry : borders.entrySet()) {
      var neighbours = new LinkedHashSet<Territory>();
      entry.getValue().stream().sorted(Comparator.comparing(indices::get)).forEach(neighbours::add);
      orderedBorders.put(entry.getKey(), Collections.unmodifiableSet(neighbours));
    }
    borders = Map.copyOf(orderedBorders);
  }

  public Set<Territory> neighbours(Territory territory) {
    return borders.getOrDefault(territory, Set.of());
  }

  public boolean areAdjacent(Territory territory1, Territory territory2) {
    return neighbours(territory1).contains(territory2);
  }
}
