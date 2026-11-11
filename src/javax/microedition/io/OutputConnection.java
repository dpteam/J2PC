package javax.microedition.io;

import java.io.DataOutputStream;
import java.io.OutputStream;

/** Connection with an output byte stream. */
public interface OutputConnection extends Connection {

    OutputStream openOutputStream() throws java.io.IOException;

    DataOutputStream openDataOutputStream() throws java.io.IOException;
}
