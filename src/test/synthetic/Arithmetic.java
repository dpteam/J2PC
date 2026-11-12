package synthetic;

/**
 * Exercises the arithmetic, conversion and comparison opcodes.
 *
 * <p>Used by the CI self-test: every construct here maps to bytecode that the
 * recompiler has to translate, and the generated source has to compile.</p>
 */
public final class Arithmetic {

    public int ints(int a, int b) {
        int sum = a + b;
        int diff = a - b;
        int prod = a * b;
        int quot = b == 0 ? 0 : a / b;
        int rem = b == 0 ? 0 : a % b;
        int shl = a << (b & 31);
        int shr = a >> (b & 31);
        int ushr = a >>> (b & 31);
        int and = a & b;
        int or = a | b;
        int xor = a ^ b;
        return sum + diff + prod + quot + rem + shl + shr + ushr + and + or + xor;
    }

    public long longs(long a, long b) {
        long sum = a + b;
        long prod = a * b;
        long shl = a << 3;
        long xor = a ^ b;
        return sum + prod + shl + xor;
    }

    public float floats(float a, float b) {
        float sum = a + b;
        float prod = a * b;
        float quot = b == 0f ? 0f : a / b;
        return sum + prod + quot;
    }

    public double doubles(double a, double b) {
        double sum = a + b;
        double prod = a * b;
        double rem = b == 0d ? 0d : a % b;
        return sum + prod + rem;
    }

    /** NaN behaviour of fcmpg/fcmpl must survive the translation. */
    public int compareFloats(float a, float b) {
        if (a < b) {
            return -1;
        }
        if (a > b) {
            return 1;
        }
        if (a == b) {
            return 0;
        }
        return 99;
    }

    public int compareDoubles(double a, double b) {
        return a < b ? -1 : (a > b ? 1 : (a == b ? 0 : 99));
    }

    public int compareLongs(long a, long b) {
        return a < b ? -1 : (a > b ? 1 : 0);
    }

    public int conversions(int i, long l, float f, double d) {
        int a = (int) l;
        int b = (int) f;
        int c = (int) d;
        long e = i;
        long g = (long) f;
        long h = (long) d;
        float j = i;
        float k = l;
        double m = i;
        double n = l;
        double o = f;
        return a + b + c + (int) e + (int) g + (int) h + (int) j + (int) k
                + (int) m + (int) n + (int) o;
    }

    public int narrowing(int v) {
        byte b = (byte) v;
        char c = (char) v;
        short s = (short) v;
        return b + c + s;
    }

    public int unary(int v) {
        return -v + (~v) + (+v);
    }

    public long unaryLong(long v) {
        return -v + (~v) + (+v);
    }

    public float unaryFloat(float v) {
        return -v + (+v);
    }

    public double unaryDouble(double v) {
        return -v + (+v);
    }

    /** Increment in its wide form, so the decoder meets the {@code wide} prefix. */
    public int wideIncrement(int seed) {
        int a = seed;
        int b = a + 1;
        int c = b + 1;
        int d = c + 1;
        int e = d + 1;
        int f = e + 1;
        int g = f + 1;
        int h = g + 1;
        int i = h + 1;
        int j = i + 1;
        int k = j + 1;
        int l = k + 1;
        int m = l + 1;
        int n = m + 1;
        int o = n + 1;
        int p = o + 1;
        int q = p + 1;
        int r = q + 1;
        int s = r + 1;
        int t = s + 1;
        return t;
    }

    private Arithmetic() {
    }
}
