package javax.wireless.messaging;

/** Binary message. */
public interface BinaryMessage extends Message {

    byte[] getPayloadData();

    void setPayloadData(byte[] data);
}
