package no.hal.risk.training;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes arrays in the format of NumPy, so that Python can read them with {@code numpy.load}: an {@code .npz} file, which is a zip
 * file with an {@code .npy} file for each array. The numbers are little-endian, in C order.
 */
public class NpzWriter implements AutoCloseable {

  private static final byte[] MAGIC = {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'};

  private final ZipOutputStream zip;

  public NpzWriter(OutputStream out) {
    this.zip = new ZipOutputStream(out);
    zip.setLevel(Deflater.BEST_SPEED);
  }

  public void floats(String name, float[] data, int... shape) throws IOException {
    var buffer = ByteBuffer.allocate(data.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asFloatBuffer().put(data);
    write(name, "<f4", shape, data.length, buffer.array());
  }

  public void ints(String name, int[] data, int... shape) throws IOException {
    var buffer = ByteBuffer.allocate(data.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asIntBuffer().put(data);
    write(name, "<i4", shape, data.length, buffer.array());
  }

  public void doubles(String name, double[] data, int... shape) throws IOException {
    var buffer = ByteBuffer.allocate(data.length * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asDoubleBuffer().put(data);
    write(name, "<f8", shape, data.length, buffer.array());
  }

  /** Unsigned bytes, e.g. 0 and 1 for a matrix of yes and no. */
  public void bytes(String name, byte[] data, int... shape) throws IOException {
    write(name, "|u1", shape, data.length, data);
  }

  private void write(String name, String type, int[] shape, int count, byte[] data) throws IOException {
    long expected = 1;
    for (int dimension : shape) {
      expected *= dimension;
    }
    if (expected != count) {
      throw new IllegalArgumentException("The shape of %s has %d values, but there are %d".formatted(name, expected, count));
    }
    zip.putNextEntry(new ZipEntry(name + ".npy"));
    zip.write(header(type, shape));
    zip.write(data);
    zip.closeEntry();
  }

  /** The header of an .npy file, version 1.0, padded so that the numbers start at a multiple of 64 bytes. */
  static byte[] header(String type, int[] shape) {
    var dimensions = new StringBuilder("(");
    for (int i = 0; i < shape.length; i++) {
      dimensions.append(shape[i]);
      if (shape.length == 1 || i < shape.length - 1) {
        dimensions.append(shape.length == 1 ? "," : ", ");
      }
    }
    dimensions.append(")");
    var text = "{'descr': '%s', 'fortran_order': False, 'shape': %s, }".formatted(type, dimensions);
    int unpadded = MAGIC.length + 2 + 2 + text.length() + 1;
    int padding = (64 - unpadded % 64) % 64;
    text = text + " ".repeat(padding) + "\n";
    var out = ByteBuffer.allocate(MAGIC.length + 2 + 2 + text.length()).order(ByteOrder.LITTLE_ENDIAN);
    out.put(MAGIC).put((byte) 1).put((byte) 0).putShort((short) text.length()).put(text.getBytes(StandardCharsets.US_ASCII));
    return out.array();
  }

  @Override
  public void close() throws IOException {
    zip.close();
  }
}
