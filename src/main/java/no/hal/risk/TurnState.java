package no.hal.risk;

public sealed interface TurnState {

  public record Reinforcing(int totalArmies) implements TurnState {
    public void reinforce(Risk risk, Territory territory, int numArmies) {
      var reinforceArmies = Math.min(numArmies, totalArmies);
      risk.reinforce(territory, numArmies);
      if (reinforceArmies < totalArmies) {
        risk.updateTurnState(new Reinforcing(totalArmies - reinforceArmies));
      } else {
        risk.nextTurnState();
      }
    }
  }
  
  public record Attacking() implements TurnState {
    public void attack(Risk risk, Territory attacker, Territory defender, int numAttackerDies, int numDefenderDies) {
      risk.attack(attacker, defender, numAttackerDies, numDefenderDies);
      risk.updateTurnState(new Attacking());
    }
  }
  
  public record Fortifying(Territory fromTerritory, Territory toTerritory) implements TurnState {
    public void fortify(Risk risk, Territory fromTerritory, Territory toTerritory, int numArmies) {
      if (fromTerritory() != null && fromTerritory != fromTerritory() && toTerritory != fromTerritory()) {
        throw new IllegalArgumentException("Cannot fortify from " + fromTerritory + " to " + toTerritory + " when already fortifying from " + fromTerritory() + " to " + toTerritory());
      }
      if (toTerritory() != null && toTerritory != toTerritory() && fromTerritory != toTerritory()) {
        throw new IllegalArgumentException("Cannot fortify from " + fromTerritory + " to " + toTerritory + " when already fortifying from " + fromTerritory() + " to " + toTerritory());
      }
      risk.fortify(fromTerritory, toTerritory, numArmies);
      risk.updateTurnState(new Fortifying(fromTerritory, toTerritory));
    }
  }
}
