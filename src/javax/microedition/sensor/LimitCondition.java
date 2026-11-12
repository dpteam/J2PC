package javax.microedition.sensor;

/** Simple threshold condition. */
public class LimitCondition implements Condition {

    private final double limit;
    private final String operator;

    public LimitCondition(double limit, String operator) {
        this.limit = limit;
        this.operator = operator == null ? ">" : operator;
    }

    @Override
    public boolean isMet(double doubleValue) {
        switch (operator) {
            case ">": return doubleValue > limit;
            case ">=": return doubleValue >= limit;
            case "<": return doubleValue < limit;
            case "<=": return doubleValue <= limit;
            case "=": return doubleValue == limit;
            default: return false;
        }
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

    public double getLimit() {
        return limit;
    }

    public String getOperator() {
        return operator;
    }
}
