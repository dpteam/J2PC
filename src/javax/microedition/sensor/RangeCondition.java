package javax.microedition.sensor;

/** Condition over a value range. */
public class RangeCondition implements Condition {

    private final double low;
    private final double high;
    private final String operator;

    public RangeCondition(double low, double high, String operator) {
        this.low = low;
        this.high = high;
        this.operator = operator == null ? "in" : operator;
    }

    @Override
    public boolean isMet(double doubleValue) {
        boolean inside = doubleValue >= low && doubleValue <= high;
        return "out".equals(operator) ? !inside : inside;
    }

    @Override
    public boolean isMet(int intValue) {
        return isMet((double) intValue);
    }

    @Override
    public boolean isMet(boolean booleanValue) {
        return false;
    }

    @Override
    public int getChannelInfo() {
        return 0;
    }

    @Override
    public boolean isNeeded() {
        return true;
    }
}
