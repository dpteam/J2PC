package javax.microedition.lcdui;

/** Bar or progress indicator. */
public class Gauge extends Item {

    public static final int INDEFINITE = -1;
    public static final int CONTINUOUS_IDLE = 0;
    public static final int INCREMENTAL_IDLE = 1;
    public static final int CONTINUOUS_RUNNING = 2;
    public static final int INCREMENTAL_UPDATING = 3;

    private int value;
    private final int maxValue;
    private final boolean interactive;

    public Gauge(String label, boolean interactive, int maxValue, int initialValue) {
        super(label);
        this.interactive = interactive;
        this.maxValue = maxValue;
        this.value = initialValue;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public boolean isInteractive() {
        return interactive;
    }

    @Override
    int height(int width) {
        return 12;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        g.setColor(0x000000);
        g.drawRect(x + 2, y + 2, width - 4, 8);
        int filled = maxValue > 0 ? (width - 6) * Math.max(0, Math.min(value, maxValue)) / maxValue : 0;
        g.fillRect(x + 3, y + 3, filled, 6);
        return 12;
    }
}
