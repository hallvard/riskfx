package no.hal.risk.training;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class NpzWriterTest {

  @Test
  public void testArraysCanBeReadBackWithTheirTypesAndShapes() throws IOException {
    var file = Files.createTempFile("riskfx", ".npz");
    try (var npz = new NpzWriter(Files.newOutputStream(file))) {
      npz.floats("matrix", new float[] {1, 2, 3, 4, 5, 6}, 2, 3);
      npz.ints("vector", new int[] {-7, 8, 9}, 3);
      npz.doubles("numbers", new double[] {0.5, Double.NaN}, 2);
      npz.bytes("flags", new byte[] {0, 1, 1, 0}, 2, 2);
      npz.floats("empty", new float[0], 0, 5);
    }
    var arrays = NpzReader.read(file);
    assertEquals(java.util.List.of("matrix", "vector", "numbers", "flags", "empty"), java.util.List.copyOf(arrays.keySet()));
    assertEquals("<f4", arrays.get("matrix").type());
    assertArrayEquals(new int[] {2, 3}, arrays.get("matrix").shape());
    assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6}, arrays.get("matrix").floats());
    assertEquals("<i4", arrays.get("vector").type());
    assertArrayEquals(new int[] {3}, arrays.get("vector").shape());
    assertArrayEquals(new int[] {-7, 8, 9}, arrays.get("vector").ints());
    assertEquals("<f8", arrays.get("numbers").type());
    assertEquals(0.5, arrays.get("numbers").doubles()[0]);
    assertTrue(Double.isNaN(arrays.get("numbers").doubles()[1]));
    assertEquals("|u1", arrays.get("flags").type());
    assertArrayEquals(new byte[] {0, 1, 1, 0}, arrays.get("flags").bytes());
    assertArrayEquals(new int[] {0, 5}, arrays.get("empty").shape());
    assertEquals(0, arrays.get("empty").floats().length);
    Files.delete(file);
  }

  @Test
  public void testTheNumbersStartAtAMultipleOf64BytesAsNumPyDoes() throws IOException {
    var file = Files.createTempFile("riskfx", ".npz");
    try (var npz = new NpzWriter(Files.newOutputStream(file))) {
      npz.floats("a", new float[3], 3);
      npz.floats("b", new float[4 * 5 * 6], 4, 5, 6);
      npz.ints("c", new int[1000], 1000);
    }
    for (var array : NpzReader.read(file).values()) {
      assertEquals(0, array.headerLength() % 64, "header of " + array.shape().length + " dimensions");
    }
    Files.delete(file);
  }

  @Test
  public void testTheHeaderIsAsNumPyWritesIt() {
    var header = new String(NpzWriter.header("<f4", new int[] {3}), java.nio.charset.StandardCharsets.US_ASCII);
    assertTrue(header.contains("{'descr': '<f4', 'fortran_order': False, 'shape': (3,), }"), header);
    assertTrue(header.endsWith("\n"));
    var matrix = new String(NpzWriter.header("<i4", new int[] {2, 3}), java.nio.charset.StandardCharsets.US_ASCII);
    assertTrue(matrix.contains("'shape': (2, 3), }"), matrix);
  }

  @Test
  public void testTheShapeMustMatchTheNumberOfValues() throws IOException {
    try (var npz = new NpzWriter(new java.io.ByteArrayOutputStream())) {
      assertThrows(IllegalArgumentException.class, () -> npz.floats("wrong", new float[5], 2, 3));
    }
  }
}
