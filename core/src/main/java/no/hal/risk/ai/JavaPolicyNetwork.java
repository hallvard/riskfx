package no.hal.risk.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a trained network in Java only, without any library, from the weights written by training.
 * It computes the same as the network that was trained (with PyTorch), step by step:
 * <ol>
 * <li>each territory's features and the global features are projected to a vector of size {@code hidden}, and normalized</li>
 * <li>{@code layers} rounds of message passing: a territory is updated from itself and its neighbours (the normalized
 *   adjacency matrix with self-loops), normalized, and added to what it was (a residual connection)</li>
 * <li>the heads: reinforcement scores each territory, attack and fortify score each pair of territories with a dot product
 *   of two projections, "end attack" and "skip fortify" are scored from the average territory vector, which also gives the value</li>
 * </ol>
 * The scores are in the layout of the {@link ActionSpace}.
 */
public class JavaPolicyNetwork implements PolicyNetwork {

  private static final float NORMALIZATION_EPSILON = 1e-5f;

  private final NetworkWeights.Config config;
  private final int n, f, g, h, layers, d;
  /** For each territory, the territories it gets messages from, itself included, and the weights of the messages. */
  private final int[][] neighbours;
  private final float[][] neighbourWeights;

  private final float[] wx, wg, b0;
  private final float[][] wn, ws, b;
  private final float[] wrn, wrg, br, wro, wqa, wka, wqf, wkf, we, be, wv1, bv1, wv2, bv2;

  /**
   * @param borders which territories are adjacent, as {@link ObservationEncoder#adjacency()}
   */
  public JavaPolicyNetwork(NetworkWeights weights, boolean[][] borders) {
    this.config = weights.config();
    this.n = borders.length;
    this.f = config.nodeFeatures();
    this.g = config.globalFeatures();
    this.h = config.hidden();
    this.layers = config.layers();
    this.d = config.attention();
    // D^-1/2 (A + I) D^-1/2, as the network was trained
    var degrees = new double[n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        degrees[i] += borders[i][j] || i == j ? 1 : 0;
      }
    }
    neighbours = new int[n][];
    neighbourWeights = new float[n][];
    for (int i = 0; i < n; i++) {
      var indices = new ArrayList<Integer>();
      for (int j = 0; j < n; j++) {
        if (borders[i][j] || i == j) {
          indices.add(j);
        }
      }
      neighbours[i] = indices.stream().mapToInt(Integer::intValue).toArray();
      neighbourWeights[i] = new float[neighbours[i].length];
      for (int k = 0; k < neighbours[i].length; k++) {
        neighbourWeights[i][k] = (float) (1 / Math.sqrt(degrees[i] * degrees[neighbours[i][k]]));
      }
    }

    wx = weights.get("Wx", f, h);
    wg = weights.get("Wg", g, h);
    b0 = weights.get("b0", h);
    wn = new float[layers][];
    ws = new float[layers][];
    b = new float[layers][];
    for (int l = 0; l < layers; l++) {
      wn[l] = weights.get("Wn" + l, h, h);
      ws[l] = weights.get("Ws" + l, h, h);
      b[l] = weights.get("b" + (l + 1), h);
    }
    wrn = weights.get("Wrn", h, h);
    wrg = weights.get("Wrg", h, h);
    br = weights.get("br", h);
    wro = weights.get("Wro", h, 1);
    wqa = weights.get("Wqa", h, d);
    wka = weights.get("Wka", h, d);
    wqf = weights.get("Wqf", h, d);
    wkf = weights.get("Wkf", h, d);
    we = weights.get("We", h, 2);
    be = weights.get("be", 2);
    wv1 = weights.get("Wv1", h, h);
    bv1 = weights.get("bv1", h);
    wv2 = weights.get("Wv2", h, 1);
    bv2 = weights.get("bv2", 1);
  }

  public static JavaPolicyNetwork load(InputStream in, boolean[][] borders) throws IOException {
    return new JavaPolicyNetwork(NetworkWeights.read(in), borders);
  }

  public NetworkWeights.Config config() {
    return config;
  }

  @Override
  public int numTerritories() {
    return n;
  }

  /**
   * Evaluates the states, on all cores if there are several, since each is independent of the others.
   */
  @Override
  public Evaluation evaluate(List<Observation> observations) {
    int actions = n + n * n + 1 + n * n + 1;
    var logits = new float[observations.size()][actions];
    var values = new float[observations.size()];
    var indices = java.util.stream.IntStream.range(0, logits.length);
    (logits.length > 1 ? indices.parallel() : indices).forEach(i -> values[i] = evaluate(observations.get(i), logits[i]));
    return new Evaluation(logits, values);
  }

  /** Adds the product of the vector {@code in} (of length {@code k}, from {@code inOffset}) and the matrix {@code w} (k x cols) to {@code out}. */
  private static void addProduct(float[] in, int inOffset, float[] w, int k, int cols, float[] out, int outOffset) {
    for (int j = 0; j < k; j++) {
      float value = in[inOffset + j];
      if (value != 0) {
        int row = j * cols;
        for (int c = 0; c < cols; c++) {
          out[outOffset + c] += value * w[row + c];
        }
      }
    }
  }

  /** Scales the vector to mean 0 and variance 1, as the network was trained. */
  private static void normalize(float[] array, int offset, int length) {
    float mean = 0;
    for (int c = 0; c < length; c++) {
      mean += array[offset + c];
    }
    mean /= length;
    float variance = 0;
    for (int c = 0; c < length; c++) {
      float centered = array[offset + c] - mean;
      variance += centered * centered;
    }
    variance /= length;
    float scale = (float) (1 / Math.sqrt(variance + NORMALIZATION_EPSILON));
    for (int c = 0; c < length; c++) {
      array[offset + c] = (array[offset + c] - mean) * scale;
    }
  }

  private static void relu(float[] array, int offset, int length) {
    for (int c = 0; c < length; c++) {
      if (array[offset + c] < 0) {
        array[offset + c] = 0;
      }
    }
  }

  private float evaluate(Observation observation, float[] logits) {
    // the first vector of each territory
    var globalPart = new float[h];
    addProduct(observation.globals(), 0, wg, g, h, globalPart, 0);
    var state = new float[n * h];
    for (int i = 0; i < n; i++) {
      for (int c = 0; c < h; c++) {
        state[i * h + c] = b0[c] + globalPart[c];
      }
      addProduct(observation.territories()[i], 0, wx, f, h, state, i * h);
      normalize(state, i * h, h);
      relu(state, i * h, h);
    }

    // message passing, with residual connections
    var received = new float[n * h];
    var update = new float[n * h];
    for (int l = 0; l < layers; l++) {
      java.util.Arrays.fill(received, 0);
      for (int i = 0; i < n; i++) {
        for (int k = 0; k < neighbours[i].length; k++) {
          int from = neighbours[i][k] * h;
          float weight = neighbourWeights[i][k];
          for (int c = 0; c < h; c++) {
            received[i * h + c] += weight * state[from + c];
          }
        }
      }
      for (int i = 0; i < n; i++) {
        System.arraycopy(b[l], 0, update, i * h, h);
        addProduct(received, i * h, wn[l], h, h, update, i * h);
        addProduct(state, i * h, ws[l], h, h, update, i * h);
        normalize(update, i * h, h);
        relu(update, i * h, h);
      }
      for (int c = 0; c < n * h; c++) {
        state[c] += update[c];
      }
    }

    // the average vector
    var average = new float[h];
    for (int i = 0; i < n; i++) {
      for (int c = 0; c < h; c++) {
        average[c] += state[i * h + c];
      }
    }
    for (int c = 0; c < h; c++) {
      average[c] /= n;
    }

    // reinforcement: one score for each territory
    var fromAverage = new float[h];
    System.arraycopy(br, 0, fromAverage, 0, h);
    addProduct(average, 0, wrg, h, h, fromAverage, 0);
    var hidden = new float[h];
    for (int i = 0; i < n; i++) {
      System.arraycopy(fromAverage, 0, hidden, 0, h);
      addProduct(state, i * h, wrn, h, h, hidden, 0);
      relu(hidden, 0, h);
      float score = 0;
      for (int c = 0; c < h; c++) {
        score += hidden[c] * wro[c];
      }
      logits[i] = score;
    }

    // attack and fortification: a score for each pair of territories
    int attackStart = n;
    int endAttack = n + n * n;
    int fortifyStart = endAttack + 1;
    int skipFortify = fortifyStart + n * n;
    pairScores(state, wqa, wka, logits, attackStart);
    pairScores(state, wqf, wkf, logits, fortifyStart);

    // ending the attack, skipping the fortification
    var ends = new float[] {be[0], be[1]};
    addProduct(average, 0, we, h, 2, ends, 0);
    logits[endAttack] = ends[0];
    logits[skipFortify] = ends[1];

    // the value
    var valueHidden = new float[h];
    System.arraycopy(bv1, 0, valueHidden, 0, h);
    addProduct(average, 0, wv1, h, h, valueHidden, 0);
    relu(valueHidden, 0, h);
    float value = bv2[0];
    for (int c = 0; c < h; c++) {
      value += valueHidden[c] * wv2[c];
    }
    return value;
  }

  private void pairScores(float[] state, float[] wq, float[] wk, float[] logits, int start) {
    var queries = new float[n * d];
    var keys = new float[n * d];
    for (int i = 0; i < n; i++) {
      addProduct(state, i * h, wq, h, d, queries, i * d);
      addProduct(state, i * h, wk, h, d, keys, i * d);
    }
    float scale = (float) (1 / Math.sqrt(d));
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        float dot = 0;
        for (int c = 0; c < d; c++) {
          dot += queries[i * d + c] * keys[j * d + c];
        }
        logits[start + i * n + j] = dot * scale;
      }
    }
  }
}
