package j2pc.rt;

/** Boxing bridge used when a primitive value has to travel through an Object slot. */
public final class Box {

    private Box() {
    }

    public static Object of(boolean v) {
        return v;
    }

    public static Object of(byte v) {
        return v;
    }

    public static Object of(char v) {
        return v;
    }

    public static Object of(short v) {
        return v;
    }

    public static Object of(int v) {
        return v;
    }

    public static Object of(long v) {
        return v;
    }

    public static Object of(float v) {
        return v;
    }

    public static Object of(double v) {
        return v;
    }

    public static int toInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : (Boolean.TRUE.equals(o) ? 1 : 0);
    }

    public static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    public static float toFloat(Object o) {
        return o instanceof Number ? ((Number) o).floatValue() : 0f;
    }

    public static double toDouble(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0d;
    }
}
