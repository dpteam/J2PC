package javax.wireless.messaging;

import java.io.IOException;

/**
 * SMS/MMS connection.
 *
 * <p>The desktop host has no radio, so sending succeeds silently and receiving
 * never delivers anything. Applications that treat "no message" as a normal state
 * keep working; ones that expect a peer degrade gracefully instead of failing at
 * an unexpected place.</p>
 */
public interface MessageConnection extends javax.microedition.io.Connection {

    String TEXT_MESSAGE = "text";
    String BINARY_MESSAGE = "binary";
    String MULTIPART_MESSAGE = "multipart";

    Message newMessage(String type);

    Message newMessage(String type, String address);

    int numberOfSegments(Message message);

    Message receive() throws IOException;

    void send(Message message) throws IOException;

    void setMessageListener(MessageListener listener) throws IOException;
}
