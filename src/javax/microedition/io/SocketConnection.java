package javax.microedition.io;

import java.io.IOException;

/** Socket connection as defined by MIDP 2.0. */
public interface SocketConnection extends StreamConnection {

    byte DELAY = 0;
    byte LINGER = 1;
    byte KEEPALIVE = 2;
    byte RCVBUF = 3;
    byte SNDBUF = 4;

    String getAddress() throws IOException;

    String getLocalAddress() throws IOException;

    int getLocalPort() throws IOException;

    int getPort() throws IOException;

    void setSocketOption(byte option, int value) throws IOException;

    int getSocketOption(byte option) throws IOException;
}
