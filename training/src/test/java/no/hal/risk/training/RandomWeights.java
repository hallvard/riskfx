package no.hal.risk.training;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import no.hal.risk.ai.NetworkWeights;

/** Weights of the shapes a network has, with random values, to test with. */
public class RandomWeights {

  public static NetworkWeights create(NetworkWeights.Config config, long seed, double std) {
    var random = new Random(seed);
    var shapes = new LinkedHashMap<String, int[]>();
    int f = config.nodeFeatures(), g = config.globalFeatures(), h = config.hidden(), d = config.attention();
    shapes.put("Wx", new int[] {f, h});
    shapes.put("Wg", new int[] {g, h});
    shapes.put("b0", new int[] {h});
    for (int l = 0; l < config.layers(); l++) {
      shapes.put("Wn" + l, new int[] {h, h});
      shapes.put("Ws" + l, new int[] {h, h});
      shapes.put("b" + (l + 1), new int[] {h});
    }
    shapes.put("Wrn", new int[] {h, h});
    shapes.put("Wrg", new int[] {h, h});
    shapes.put("br", new int[] {h});
    shapes.put("Wro", new int[] {h, 1});
    for (var name : List.of("Wqa", "Wka", "Wqf", "Wkf")) {
      shapes.put(name, new int[] {h, d});
    }
    shapes.put("We", new int[] {h, 2});
    shapes.put("be", new int[] {2});
    shapes.put("Wv1", new int[] {h, h});
    shapes.put("bv1", new int[] {h});
    shapes.put("Wv2", new int[] {h, 1});
    shapes.put("bv2", new int[] {1});
    var tensors = new LinkedHashMap<String, NetworkWeights.Tensor>();
    shapes.forEach((name, shape) -> {
      int size = 1;
      for (int dimension : shape) {
        size *= dimension;
      }
      var values = new float[size];
      for (int i = 0; i < size; i++) {
        values[i] = (float) (random.nextGaussian() * std);
      }
      tensors.put(name, new NetworkWeights.Tensor(shape, values));
    });
    return new NetworkWeights(config, tensors);
  }

  /** The size of the network of the game, with the features of the observations. */
  public static NetworkWeights.Config config(int hidden, int layers, int attention) {
    return new NetworkWeights.Config(13, 14, hidden, layers, attention);
  }
}
