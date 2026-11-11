package j2pc.rt;

/**
 * Comparison helpers matching the exact JVM semantics of {@code fcmpg},
 * {@code fcmpl}, {@code dcmpg}, {@code dcmpl} and {@code lcmp}, including the
 * NaN handling that plain Java relational operators do not provide.
 */
public final class Cmp {

    private Cmp() {
    }

    public static int fcmpg(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) {
            return 1;
        }
        return a > b ? 1 : (a == b ? 0 : -1);
    }

    public static int fcmpl(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) {
            return -1;
        }
        return a > b ? 1 : (a == b ? 0 : -1);
    }

    public static int dcmpg(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return 1;
        }
        return a > b ? 1 : (a == b ? 0 : -1);
    }

    public static int dcmpl(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return -1;
        }
        return a > b ? 1 : (a == b ? 0 : -1);
    }

    public static int lcmp(long a, long b) {
        return a > b ? 1 : (a == b ? 0 : -1);
    }
}
