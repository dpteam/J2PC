package javax.microedition.sensor;

/** Value range of a channel. */
public interface MeasurementRange {

    double getMaxValue();

    double getMinValue();

    double getResolution();
}
