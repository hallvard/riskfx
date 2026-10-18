package no.hal.risk;

/**
 * Which territories armies can be moved to when fortifying.
 */
public enum FortifyMode {
  /** Only to adjacent territories with the same occupant (simple, few actions). */
  ADJACENT,
  /** To all territories reachable through adjacent territories with the same occupant (standard rules). */
  CONNECTED
}
