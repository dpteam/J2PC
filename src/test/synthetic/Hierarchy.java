package synthetic;

/**
 * Exercises inheritance, inner and anonymous classes, static initialisers,
 * synthetic accessors and {@code <clinit>}.
 */
public class Hierarchy {

    protected int base;
    private int hidden;

    /** Static initialiser with a non-trivial body. */
    private static final int[] TABLE;
    private static final String NAME;
    static int initialised;

    static {
        TABLE = new int[16];
        for (int i = 0; i < TABLE.length; i++) {
            TABLE[i] = i * 3;
        }
        NAME = "hierarchy" + TABLE.length;
        initialised = 1;
    }

    public Hierarchy() {
        this(0);
    }

    public Hierarchy(int base) {
        super();
        this.base = base;
        this.hidden = base * 2;
    }

    public int getHidden() {
        return hidden;
    }

    protected void setHidden(int v) {
        this.hidden = v;
    }

    public int table(int i) {
        return TABLE[i % TABLE.length];
    }

    public String name() {
        return NAME;
    }

    /** Overridden by the subclass; the call is virtual. */
    public int value() {
        return base;
    }

    /** Inner (non-static) class: holds an outer reference. */
    public final class Inner {

        private int depth;

        public Inner(int depth) {
            this.depth = depth;
        }

        /** Reaches the private field of the outer instance through an accessor. */
        public int peek() {
            return hidden + depth;
        }

        public int outerValue() {
            return value();
        }
    }

    /** Static nested class. */
    public static final class Nested {

        private final int v;

        public Nested(int v) {
            this.v = v;
        }

        public int value() {
            return v;
        }
    }

    /** Anonymous class: compiles to {@code Hierarchy$1}. */
    public Runnable task() {
        final int captured = base + 1;
        return new Runnable() {
            @Override
            public void run() {
                setHidden(getHidden() + captured);
            }
        };
    }

    /** Anonymous class implementing an interface. */
    public Shape shape(final int w, final int h) {
        return new Shape() {
            @Override
            public int area() {
                return w * h;
            }

            @Override
            public int corners() {
                return CORNERS_SQUARE;
            }
        };
    }

    /** Subclass in the same file, so a synthetic accessor is generated. */
    public static final class Derived extends Hierarchy {

        private final String label;

        public Derived(int base, String label) {
            super(base);
            this.label = label;
        }

        @Override
        public int value() {
            // Touches a protected member and calls a protected method.
            setHidden(base * 10);
            return super.value() + getHidden();
        }

        public String label() {
            return label;
        }
    }

    /** Varargs compile to an array parameter. */
    public int sum(int... values) {
        int total = 0;
        for (int v : values) {
            total += v;
        }
        return total;
    }

    /** Enum: compiles to a class with a {@code $VALUES} array and {@code values()}. */
    public enum Mode {
        IDLE,
        RUNNING,
        DONE;

        public boolean isTerminal() {
            return this == DONE;
        }
    }

    public Mode mode() {
        return Mode.RUNNING;
    }

    public boolean terminal() {
        return Mode.DONE.isTerminal();
    }
}
