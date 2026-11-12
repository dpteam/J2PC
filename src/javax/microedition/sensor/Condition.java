package javax.microedition.sensor;

/** Condition that triggers a sensor callback. */
public interface Condition {

    boolean isMet(double doubleValue);

    boolean isMet(int intValue);

    boolean isMet(boolean booleanValue);

    int getChannelInfo();

    boolean isNeeded();
}
