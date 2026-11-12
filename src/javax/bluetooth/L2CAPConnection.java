package javax.bluetooth;

import javax.microedition.io.StreamConnection;

/** L2CAP channel. */
public interface L2CAPConnection extends javax.microedition.io.Connection {

    int DEFAULT_MTU = 672;
    int MINIMUM_MTU = 48;

    int getTransmitMTU() throws java.io.IOException;

    int getReceiveMTU() throws java.io.IOException;

    void send(byte[] data) throws java.io.IOException;

    int receive(byte[] inBuf) throws java.io.IOException;

    boolean ready() throws java.io.IOException;
}
