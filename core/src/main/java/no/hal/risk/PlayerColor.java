package no.hal.risk;

/**
 * The color of a player, as shares of red, green and blue, from 0 to 1.
 * It is a type of its own, so the rules don't depend on a user interface library.
 */
public record PlayerColor(double red, double green, double blue) {

  /** From a color written as in html, as a number, e.g. 0xA0724A. */
  public static PlayerColor rgb(int rgb) {
    return new PlayerColor(((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0);
  }

  /** How different two colors are, as the distance between them in the color cube, from 0 up to about 1.7. */
  public double distanceTo(PlayerColor other) {
    return Math.sqrt(Math.pow(red - other.red, 2) + Math.pow(green - other.green, 2) + Math.pow(blue - other.blue, 2));
  }
}
