package javax.bluetooth;

/** Thrown when the Bluetooth stack is not available. */
public class BluetoothStateException extends java.io.IOException {

    private static final long serialVersionUID = 1L;

    public BluetoothStateException() {
    }

    public BluetoothStateException(String message) {
        super(message);
    }
}
