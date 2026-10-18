package no.hal.risk.training;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Reads .npz files as NumPy writes them, to check what is written for Python. For tests. */
public class NpzReader {

  /** An array: its type, e.g. {@code <f4}, its shape, and its numbers as bytes. */
  public record Array(String type, int[] shape, ByteBuffer data, int headerLength) {

    public int size() {
      int size = 1;
      for (int dimension : shape) {
        size *= dimension;
      }
      return size;
    }

    public float[] floats() {
      var result = new float[size()];
      data.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(result);
      return result;
    }

    public int[] ints() {
      var result = new int[size()];
      data.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(result);
      return result;
    }

    public double[] doubles() {
      var result = new double[size()];
      data.duplicate().order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(result);
      return result;
    }

    public byte[] bytes() {
      var result = new byte[size()];
      data.duplicate().get(result);
      return result;
    }
  }

  public static Map<String, Array> read(Path path) throws IOException {
    var arrays = new LinkedHashMap<String, Array>();
    try (var zip = new ZipInputStream(Files.newInputStream(path))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        var bytes = new ByteArrayOutputStream();
        zip.transferTo(bytes);
        arrays.put(entry.getName().replaceAll("\\.npy$", ""), parse(bytes.toByteArray()));
      }
    }
    return arrays;
  }

  static Array parse(byte[] bytes) {
    var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    if ((bytes[0] & 0xFF) != 0x93 || bytes[1] != 'N' || bytes[2] != 'U' || bytes[3] != 'M' || bytes[4] != 'P' || bytes[5] != 'Y') {
      throw new IllegalArgumentException("Not an .npy file");
    }
    if (bytes[6] != 1 || bytes[7] != 0) {
      throw new IllegalArgumentException("Not version 1.0");
    }
    int headerLength = buffer.getShort(8) & 0xFFFF;
    var header = new String(bytes, 10, headerLength, StandardCharsets.US_ASCII);
    var type = find(header, "'descr': '([^']*)'");
    var shapeText = find(header, "'shape': \\(([^)]*)\\)");
    var shape = shapeText.isBlank() ? new int[0] : Arrays.stream(shapeText.split(",")).map(String::strip).filter(s -> ! s.isEmpty()).mapToInt(Integer::parseInt).toArray();
    if (! find(header, "'fortran_order': (\\w+)").equals("False")) {
      throw new IllegalArgumentException("Fortran order is not supported");
    }
    var data = ByteBuffer.wrap(bytes, 10 + headerLength, bytes.length - 10 - headerLength).slice();
    return new Array(type, shape, data, 10 + headerLength);
  }

  private static String find(String text, String regex) {
    var matcher = Pattern.compile(regex).matcher(text);
    if (! matcher.find()) {
      throw new IllegalArgumentException("No " + regex + " in " + text);
    }
    return matcher.group(1);
  }
}
