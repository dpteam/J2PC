package javax.microedition.sensor;

/** Callback for sensor availability changes. */
public interface SensorListener {

    void sensorAvailable(SensorInfo info);

    void sensorUnavailable(SensorInfo info);
}
