package synthetic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Exercises {@code invokedynamic}: lambdas and compiled string concatenation.
 *
 * <p>J2ME code never contains these, but suites recompiled from newer toolchains
 * do, and the recompiler has to translate the call sites instead of giving up.</p>
 */
public final class Modern {

    /** String concatenation compiles to a StringConcatFactory call site. */
    public String concat(int a, String b, double c) {
        return "a=" + a + ";b=" + b + ";c=" + c;
    }

    /** A concatenation with a constant in the middle. */
    public String constantMiddle(String x) {
        return "[" + x + "]";
    }

    /** Lambda capturing a local. */
    public Supplier<String> supplier(final String prefix) {
        return () -> prefix + "-suffix";
    }

    /** Lambda capturing nothing. */
    public Runnable noCapture() {
        return () -> {
            int ignored = 1 + 1;
            if (ignored < 0) {
                throw new IllegalStateException();
            }
        };
    }

    /** Lambda with arguments and a return value. */
    public Function<Integer, Integer> doubler() {
        return v -> v * 2;
    }

    public BiFunction<Integer, Integer, Integer> adder() {
        return (a, b) -> a + b;
    }

    /** Method reference to a static method of this class. */
    public Function<String, Integer> lengthOf() {
        return Modern::length;
    }

    /** Method reference to an instance method of an arbitrary object. */
    public Function<String, Integer> lengthOfInstance() {
        return String::length;
    }

    /** Method reference to a constructor. */
    public Function<String, StringBuilder> builder() {
        return StringBuilder::new;
    }

    /** Comparator lambda, used through a collection. */
    public List<String> sorted(List<String> input) {
        List<String> copy = new ArrayList<>(input);
        copy.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        return copy;
    }

    /** Lambda stored in a field. */
    private final Function<Integer, Integer> square = v -> v * v;

    public int useSquare(int v) {
        return square.apply(v);
    }

    /** Lambda inside a loop captures the loop variable. */
    public int sumOfSquares(int n) {
        int[] values = new int[n];
        for (int i = 0; i < n; i++) {
            final int captured = i;
            Function<Integer, Integer> f = v -> v + captured;
            values[i] = f.apply(i);
        }
        return Arrays.stream(values).sum();
    }

    public static int length(String s) {
        return s == null ? 0 : s.length();
    }

    private Modern() {
    }
}
