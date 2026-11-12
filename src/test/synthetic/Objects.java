package synthetic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Exercises references: arrays of every kind, multidimensional arrays, strings,
 * collections, and the {@code dup}/{@code swap} family.
 */
public final class Objects {

    public int intArray(int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i * i;
        }
        int total = 0;
        for (int value : a) {
            total += value;
        }
        return total;
    }

    public long longArray(int n) {
        long[] a = new long[n];
        a[0] = 1L;
        return a.length + a[0];
    }

    public float floatArray(int n) {
        float[] a = new float[n];
        a[0] = 1.5f;
        return a.length + a[0];
    }

    public double doubleArray(int n) {
        double[] a = new double[n];
        a[0] = 2.5d;
        return a.length + a[0];
    }

    public int booleanArray(int n) {
        boolean[] a = new boolean[n];
        a[0] = true;
        int count = 0;
        for (boolean b : a) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    public int charArray(String s) {
        char[] a = s.toCharArray();
        int total = 0;
        for (char c : a) {
            total += c;
        }
        return total;
    }

    public int byteArray(int n) {
        byte[] a = new byte[n];
        a[0] = (byte) 200;
        return a[0] + a.length;
    }

    public int shortArray(int n) {
        short[] a = new short[n];
        a[0] = (short) 40000;
        return a[0] + a.length;
    }

    /** {@code multianewarray} with two and three dimensions. */
    public int multiDim(int n) {
        int[][] grid = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                grid[i][j] = i + j;
            }
        }
        int[][][] cube = new int[2][3][4];
        cube[1][2][3] = 7;
        return grid[n - 1][n - 1] + cube[1][2][3];
    }

    /** Ragged array: {@code anewarray} of a component that is itself an array. */
    public int ragged(int n) {
        int[][] rows = new int[n][];
        for (int i = 0; i < n; i++) {
            rows[i] = new int[i + 1];
        }
        int total = 0;
        for (int[] row : rows) {
            total += row.length;
        }
        return total;
    }

    public String[] stringArray(int n) {
        String[] a = new String[n];
        a[0] = "x";
        return a;
    }

    /** String concatenation compiles to StringBuffer/StringBuilder chains. */
    public String concat(int a, long b, String c, char d) {
        return "a=" + a + ",b=" + b + ",c=" + c + ",d=" + d;
    }

    public String stringOps(String s) {
        return s.trim().toUpperCase().substring(0, Math.min(3, s.length()));
    }

    public int collections(List<String> list) {
        List<String> copy = new ArrayList<>(list);
        copy.add("extra");
        int total = 0;
        for (Iterator<String> it = copy.iterator(); it.hasNext(); ) {
            total += it.next().length();
        }
        return total;
    }

    public int maps(Map<String, Integer> map) {
        Map<String, Integer> copy = new HashMap<>(map);
        copy.put("x", 1);
        int total = 0;
        for (Map.Entry<String, Integer> e : copy.entrySet()) {
            total += e.getValue();
        }
        return total;
    }

    /** The classic {@code dup} pattern: field assignment from a method result. */
    private int counter;

    public int incrementAndGet() {
        return ++counter;
    }

    public int getAndIncrement() {
        return counter++;
    }

    /** {@code dup_x1} pattern from post-increment on an array element. */
    public int postIncrement(int[] a, int i) {
        return a[i]++;
    }

    public int preIncrement(int[] a, int i) {
        return ++a[i];
    }

    /** Long field post-increment uses {@code dup2_x1}. */
    private long bigCounter;

    public long postIncrementLong() {
        return bigCounter++;
    }

    /** Compound assignment on a field. */
    public int addToField(int delta) {
        counter += delta;
        return counter;
    }

    /** instanceof and checkcast. */
    public int typeChecks(Object o) {
        if (o instanceof String) {
            String s = (String) o;
            return s.length();
        }
        if (o instanceof int[]) {
            return ((int[]) o).length;
        }
        return -1;
    }

    /** Class literals and arrays of them. */
    public Class<?>[] classes() {
        return new Class<?>[]{String.class, Integer.class, int[].class, Objects.class};
    }

    public Object[] mixed(Object a, int b, long c, double d, boolean e) {
        return new Object[]{a, b, c, d, e};
    }

    private Objects() {
    }
}
