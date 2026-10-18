package no.hal.risk;

public interface RiskListener {
  public void territoryUpdated(Risk risk, Territory territory);
  public void playerTurnStateUpdated(Risk risk, Player player, TurnState turnState);
}
