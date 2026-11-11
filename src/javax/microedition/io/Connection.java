package javax.microedition.io;

import java.io.IOException;

/** Base interface of all J2ME connections. */
public interface Connection {

    void close() throws IOException;
}
