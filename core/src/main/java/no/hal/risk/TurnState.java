package no.hal.risk;

public sealed interface TurnState {

  public record Reinforcement(Territory territory, int numArmies) {
  }
  public record Reinforcing(int totalArmies) implements TurnState {
    public void reinforce(Risk risk, Territory territory, int numArmies) {
      var reinforceArmies = Math.min(numArmies, totalArmies);
      risk.reinforce(territory, reinforceArmies);
      if (reinforceArmies < totalArmies) {
        risk.updateTurnState(new Reinforcing(totalArmies - reinforceArmies));
      } else {
        risk.nextTurnState();
      }
    }
    public void reinforce(Risk risk, Reinforcement reinforce) {
      reinforce(risk, reinforce.territory(), reinforce.numArmies());
    }
  }
  
  public record Attack(Territory attacker, Territory defender, int numAttackerDies, int numDefenderDies) {
  }
  public record Attacking() implements TurnState {
    public boolean attack(Risk risk, Territory attacker, Territory defender, int numAttackerDies, int numDefenderDies) {
      boolean defenderDefeated = risk.attack(attacker, defender, numAttackerDies, numDefenderDies);
      if (risk.isGameOver()) {
        risk.nextTurnState();
      } else {
        risk.updateTurnState(new Attacking());
      }
      return defenderDefeated;
    }
    public boolean attack(Risk risk, Attack attack) {
      return attack(risk, attack.attacker(), attack.defender(), attack.numAttackerDies(), attack.numDefenderDies());
    }
  }
  
  public record Fortification(Territory fromTerritory, Territory toTerritory, int numArmies) {
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
    public void fortify(Risk risk, Fortification fortify) {
      fortify(risk, fortify.fromTerritory(), fortify.toTerritory(), fortify.numArmies());
    }
  }

  public record GameOver(Player winner) implements TurnState {
  }
}
