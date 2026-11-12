package synthetic;

/**
 * Exercises the exception table: typed catches, finally blocks, rethrow,
 * nested handlers and try-with-resources style cleanup.
 */
public final class Exceptions {

    public int typedCatch(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int twoCatches(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        } catch (RuntimeException e) {
            return -2;
        }
    }

    /** Compiles to duplicated handler code, not to a real finally block. */
    public int withFinally(int v) {
        int result = 0;
        try {
            if (v == 0) {
                throw new IllegalArgumentException("zero");
            }
            result = 100 / v;
        } catch (ArithmeticException e) {
            result = -1;
        } finally {
            result += 1000;
        }
        return result;
    }

    /** finally without a catch, so the exception must propagate. */
    public int finallyRethrow(int v) {
        int marker = 0;
        try {
            marker = 1;
            if (v < 0) {
                throw new IllegalStateException("negative");
            }
            marker = 2;
        } finally {
            marker += 10;
        }
        return marker;
    }

    public void rethrow() {
        try {
            throw new UnsupportedOperationException("boom");
        } catch (RuntimeException e) {
            throw e;
        }
    }

    /** Nested try inside catch. */
    public int nested(String a, String b) {
        try {
            try {
                return Integer.parseInt(a) / Integer.parseInt(b);
            } catch (NumberFormatException e) {
                return -1;
            }
        } catch (ArithmeticException e) {
            return -2;
        }
    }

    /** Catch of a supertype, then a more specific one in an inner block. */
    public int ordered(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            try {
                return Integer.parseInt(s.trim());
            } catch (RuntimeException inner) {
                return -3;
            }
        }
    }

    /** Synchronized block compiles to monitorenter/monitorexit with a handler. */
    public int synchronizedBlock(Object lock, int[] counter) {
        synchronized (lock) {
            counter[0]++;
            return counter[0];
        }
    }

    /** Synchronized method: the monitor is implicit, no bytecode is emitted. */
    public synchronized int synchronizedMethod(int v) {
        return v * 2;
    }

    /** Throw inside a loop inside a try. */
    public int throwInLoop(int n) {
        int total = 0;
        try {
            for (int i = 0; i < n; i++) {
                if (i == 5) {
                    throw new IndexOutOfBoundsException("five");
                }
                total += i;
            }
        } catch (IndexOutOfBoundsException e) {
            total = -total;
        }
        return total;
    }

    private Exceptions() {
    }
}
