package javax.wireless.messaging;

/** Text message. */
public interface TextMessage extends Message {

    String getPayloadText();

    void setPayloadText(String data);
}
