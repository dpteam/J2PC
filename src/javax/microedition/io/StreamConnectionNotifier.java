package javax.microedition.io;

import java.io.IOException;

/** Factory of stream connections. */
public interface StreamConnectionNotifier extends Connection {

    StreamConnection acceptAndOpen() throws IOException;
}
