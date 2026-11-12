package javax.microedition.sensor;

import java.io.IOException;

/**
 * A connection to a sensor.
 *
 * <p>No sensor hardware exists on a desktop, so {@link SensorManager#findSensors}
 * returns an empty array. The interfaces are complete, so code that enumerates
 * sensors, checks availability and gives up gracefully keeps working.</p>
 */
public interface SensorConnection extends javax.microedition.io.Connection {

    int STATE_CLOSED = 4;
    int STATE_LISTENING = 2;
    int STATE_OPENED = 1;
    int STATE_STOPPED = 3;

    int BUFFER_SIZE = 0;
    int CONTEXT_TYPE = 1;
    int MAX_BUFFER_SIZE = 2;
    int QUANTITY = 3;
    int URL = 4;

    SensorInfo getSensorInfo();

    int getState();

    void setDataListener(DataListener listener, int bufferSize);

    void setDataListener(DataListener listener, Data[] buffer, boolean isDataLost);

    void removeDataListener();

    Data[] getData(int bufferSize) throws IOException;

    Data[] getData(int bufferSize, long bufferingPeriod, boolean isTimestampIncluded,
                   boolean isUncertaintyIncluded, boolean isValidityIncluded) throws IOException;

    void startDataListening(DataListener listener, int bufferSize, long bufferingPeriod,
                            boolean isTimestampIncluded, boolean isUncertaintyIncluded,
                            boolean isValidityIncluded) throws IOException;

    void stopDataListening();
}
