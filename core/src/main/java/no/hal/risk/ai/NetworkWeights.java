package no.hal.risk.ai;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The weights of a network, as written by training and read to run the network, in a plain format that needs no libraries:
 * the number 'RFXW', the version, the size of the network, and then the name, shape and values of each array.
 */
public final class NetworkWeights {

  private static final int MAGIC = 0x52465857;
  private static final int VERSION = 2;

  /** The size of a network, which doesn't depend on the map: the territories and borders are given when the network is run. */
  public record Config(int nodeFeatures, int globalFeatures, int hidden, int layers, int attention) {
  }

  /** An array of numbers, with its dimensions. */
  public record Tensor(int[] shape, float[] data) {

    public Tensor {
      long size = 1;
      for (int dimension : shape) {
        size *= dimension;
      }
      if (size != data.length) {
        throw new IllegalArgumentException("A tensor of shape %s has %d values, not %d".formatted(java.util.Arrays.toString(shape), size, data.length));
      }
    }
  }

  private final Config config;
  private final Map<String, Tensor> tensors;

  public NetworkWeights(Config config, Map<String, Tensor> tensors) {
    this.config = config;
    this.tensors = new LinkedHashMap<>(tensors);
  }

  public Config config() {
    return config;
  }

  public Map<String, Tensor> tensors() {
    return tensors;
  }

  /** The array with the name, which must have the shape. */
  public float[] get(String name, int... shape) {
    var tensor = tensors.get(name);
    if (tensor == null) {
      throw new IllegalArgumentException("The weights have no array " + name);
    }
    if (! java.util.Arrays.equals(tensor.shape(), shape)) {
      throw new IllegalArgumentException("The array %s has the shape %s, not the expected %s".formatted(
          name, java.util.Arrays.toString(tensor.shape()), java.util.Arrays.toString(shape)));
    }
    return tensor.data();
  }

  public void write(OutputStream out) throws IOException {
    var data = new DataOutputStream(new java.io.BufferedOutputStream(out));
    data.writeInt(MAGIC);
    data.writeInt(VERSION);
    data.writeInt(config.nodeFeatures());
    data.writeInt(config.globalFeatures());
    data.writeInt(config.hidden());
    data.writeInt(config.layers());
    data.writeInt(config.attention());
    data.writeInt(tensors.size());
    for (var entry : tensors.entrySet()) {
      data.writeUTF(entry.getKey());
      var tensor = entry.getValue();
      data.writeInt(tensor.shape().length);
      for (int dimension : tensor.shape()) {
        data.writeInt(dimension);
      }
      for (float value : tensor.data()) {
        data.writeFloat(value);
      }
    }
    data.flush();
  }

  /** Whether the first bytes are those of a file with weights, as opposed to some other format. */
  public static boolean isWeights(byte[] firstBytes) {
    return firstBytes.length >= 4 && ((firstBytes[0] & 0xFF) << 24 | (firstBytes[1] & 0xFF) << 16 | (firstBytes[2] & 0xFF) << 8 | (firstBytes[3] & 0xFF)) == MAGIC;
  }

  public static NetworkWeights read(InputStream in) throws IOException {
    var data = new DataInputStream(new java.io.BufferedInputStream(in));
    if (data.readInt() != MAGIC) {
      throw new IOException("Not a file with network weights");
    }
    int version = data.readInt();
    if (version != VERSION) {
      throw new IOException("Unknown version of the weights: " + version);
    }
    var config = new Config(data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt());
    int count = data.readInt();
    var tensors = new LinkedHashMap<String, Tensor>();
    for (int i = 0; i < count; i++) {
      var name = data.readUTF();
      var shape = new int[data.readInt()];
      long size = 1;
      for (int d = 0; d < shape.length; d++) {
        shape[d] = data.readInt();
        size *= shape[d];
      }
      if (size > 50_000_000) {
        throw new IOException("An array of %d values is not a network of this kind".formatted(size));
      }
      var values = new float[(int) size];
      for (int v = 0; v < values.length; v++) {
        values[v] = data.readFloat();
      }
      tensors.put(name, new Tensor(shape, values));
    }
    return new NetworkWeights(config, tensors);
  }
}
