package javax.microedition.io.file;

import java.io.IOException;

/** Thrown when an operation is attempted on a closed connection. */
public class ConnectionClosedException extends IOException {

    private static final long serialVersionUID = 1L;

    public ConnectionClosedException() {
    }

    public ConnectionClosedException(String message) {
        super(message);
    }
}
