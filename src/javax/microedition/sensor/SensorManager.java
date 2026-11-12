package javax.microedition.sensor;

import java.io.IOException;

/**
 * Entry point of the sensor API.
 *
 * <p>Returns an empty sensor list rather than throwing: an application that
 * probes for sensors and adapts is the normal case, and the desktop host honestly
 * has none.</p>
 */
public final class SensorManager {

    public static final int NUM_SENSOR_INFOS = 0;

    private SensorManager() {
    }

    public static SensorInfo[] findSensors() {
        return new SensorInfo[0];
    }

    public static SensorInfo[] findSensors(String quantity, String contextType) {
        return new SensorInfo[0];
    }

    public static SensorInfo[] findSensors(String url) {
        return new SensorInfo[0];
    }

    public static SensorInfo getSensorInfo(int index) {
        return null;
    }

    public static int getSensorCount() {
        return 0;
    }

    public static void addSensorListener(SensorListener listener) {
    }

    public static void removeSensorListener(SensorListener listener) {
    }
}
