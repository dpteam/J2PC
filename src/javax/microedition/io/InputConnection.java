package javax.microedition.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Connection with a byte stream. */
public interface InputConnection extends Connection {

    InputStream openInputStream() throws java.io.IOException;

    DataInputStream openDataInputStream() throws java.io.IOException;
}
