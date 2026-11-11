package javax.microedition.io;

import java.io.IOException;

/** Thrown when a connection cannot be established. */
public class ConnectionNotFoundException extends IOException {

    private static final long serialVersionUID = 1L;

    public ConnectionNotFoundException() {
        super();
    }

    public ConnectionNotFoundException(String message) {
        super(message);
    }
}
