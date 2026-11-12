package javax.bluetooth;

/** Thrown when a UUID string is malformed. */
public class BluetoothConnectionException extends java.io.IOException {

    private static final long serialVersionUID = 1L;

    public static final int UNKNOWN = 1;
    public static final int FAILED = 2;
    public static final int SECURITY_BLOCK = 3;
    public static final int NO_RESOURCES = 4;
    public static final int TIMEOUT = 5;

    private final int status;

    public BluetoothConnectionException(int status) {
        this.status = status;
    }

    public BluetoothConnectionException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
