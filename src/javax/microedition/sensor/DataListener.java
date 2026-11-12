package javax.microedition.sensor;

/** Callback for data availability. */
public interface DataListener {

    void dataReceived(SensorConnection sensor, Data[] data, boolean isDataLost);
}
