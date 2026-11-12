package javax.bluetooth;

/** Factory of L2CAP channels. */
public interface L2CAPConnectionNotifier extends javax.microedition.io.Connection {

    L2CAPConnection acceptAndOpen() throws java.io.IOException;
}
