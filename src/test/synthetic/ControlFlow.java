package synthetic;

/**
 * Exercises every control-flow shape: dense and sparse switches, nested loops,
 * labelled breaks, and irreducible-looking jumps.
 */
public final class ControlFlow {

    /** Compiles to {@code tableswitch}: dense, small keys. */
    public int denseSwitch(int v) {
        switch (v) {
            case 0:
                return 10;
            case 1:
                return 11;
            case 2:
                return 12;
            case 3:
                return 13;
            case 4:
                return 14;
            default:
                return -1;
        }
    }

    /** Compiles to {@code lookupswitch}: sparse keys. */
    public int sparseSwitch(int v) {
        switch (v) {
            case -1000:
                return 1;
            case 0:
                return 2;
            case 7:
                return 3;
            case 4242:
                return 4;
            case 100000:
                return 5;
            default:
                return 0;
        }
    }

    /** Switch with fall-through and side effects. */
    public int fallThrough(int v, int[] out) {
        int total = 0;
        switch (v) {
            case 1:
                total += 1;
                out[0] = total;
                // falls through
            case 2:
                total += 2;
                out[1] = total;
                // falls through
            case 3:
                total += 3;
                break;
            default:
                total = -1;
        }
        return total;
    }

    public int nestedLoops(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i * j > n) {
                    continue;
                }
                total += i * j;
            }
        }
        return total;
    }

    public int whileWithBreak(int limit) {
        int i = 0;
        while (true) {
            if (i >= limit) {
                break;
            }
            i++;
        }
        return i;
    }

    public int doWhile(int n) {
        int i = 0;
        do {
            i += 2;
        } while (i < n);
        return i;
    }

    /** Ternary compiles to a diamond with two converging assignments. */
    public int ternary(int a, int b) {
        int max = a > b ? a : b;
        int min = a < b ? a : b;
        return max - min;
    }

    /** Chained comparisons produce a long branch chain. */
    public String classify(int v) {
        if (v < 0) {
            return "negative";
        } else if (v == 0) {
            return "zero";
        } else if (v < 10) {
            return "small";
        } else if (v < 100) {
            return "medium";
        }
        return "large";
    }

    /** Labeled break out of nested loops. */
    public int labeledBreak(int[][] grid) {
        int found = -1;
        outer:
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                if (grid[i][j] == 0) {
                    found = i * 100 + j;
                    break outer;
                }
            }
        }
        return found;
    }

    /** Boolean short-circuit produces conditional jumps. */
    public boolean logic(boolean a, boolean b, int x, int y) {
        return (a && b) || (x > y && !a) || (x == y);
    }

    /** Conditional expressions on references. */
    public String pick(String a, String b) {
        return a != null ? a : b;
    }

    /** Null checks. */
    public int nullCheck(String s) {
        if (s == null) {
            return 0;
        }
        return s.length();
    }

    private ControlFlow() {
    }
}
