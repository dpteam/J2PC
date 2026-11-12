package javax.wireless.messaging;

/** Receives incoming messages. */
public interface MessageListener {

    void notifyIncomingMessage(MessageConnection conn);
}
