package javax.microedition.sensor;

/** A single sensor reading. */
public interface Data {

    int TYPE_DOUBLE = 1;
    int TYPE_INT = 2;
    int TYPE_BOOLEAN = 3;

    ChannelInfo getChannelInfo();

    double[] getDoubleValues();

    int[] getIntValues();

    boolean[] getBooleanValues();

    long getTimestamp();

    int getDataType();
}
