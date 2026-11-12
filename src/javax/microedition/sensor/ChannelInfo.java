package javax.microedition.sensor;

/** Metadata of one channel of a sensor. */
public interface ChannelInfo {

    int TYPE_DOUBLE = 1;
    int TYPE_INT = 2;
    int TYPE_BOOLEAN = 3;

    float getAccuracy();

    int getDataType();

    float getScale();

    String getUnit();

    Condition[] getConditions();

    MeasurementRange getMeasurementRange();

    String getName();
}
