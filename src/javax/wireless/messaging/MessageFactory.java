package javax.wireless.messaging;

import java.io.IOException;

/**
 * Concrete messages and the connection that carries them.
 *
 * <p>Implemented as plain value objects: {@code Connector.open("sms://…")} returns
 * one of these, and everything except actual transmission works, which is what
 * recompiled code exercises at start-up.</p>
 */
public final class MessageFactory {

    private MessageFactory() {
    }

    /** Creates a message of the requested type. */
    public static Message create(String type) {
        if (MessageConnection.BINARY_MESSAGE.equals(type)) {
            return new Binary();
        }
        if (MessageConnection.MULTIPART_MESSAGE.equals(type)) {
            return new Multipart();
        }
        return new Text();
    }

    /** Minimal {@link TextMessage}. */
    static final class Text implements TextMessage {
        private String address = "";
        private String payload = "";
        private final java.util.Date timestamp = new java.util.Date();

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public void setAddress(String address) {
            this.address = address == null ? "" : address;
        }

        @Override
        public java.util.Date getTimestamp() {
            return timestamp;
        }

        @Override
        public String getPayloadText() {
            return payload;
        }

        @Override
        public void setPayloadText(String data) {
            this.payload = data == null ? "" : data;
        }
    }

    /** Minimal {@link BinaryMessage}. */
    static final class Binary implements BinaryMessage {
        private String address = "";
        private byte[] payload = new byte[0];
        private final java.util.Date timestamp = new java.util.Date();

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public void setAddress(String address) {
            this.address = address == null ? "" : address;
        }

        @Override
        public java.util.Date getTimestamp() {
            return timestamp;
        }

        @Override
        public byte[] getPayloadData() {
            return payload;
        }

        @Override
        public void setPayloadData(byte[] data) {
            this.payload = data == null ? new byte[0] : data;
        }
    }

    /** Minimal {@link MultipartMessage}. */
    static final class Multipart implements MultipartMessage {
        private String address = "";
        private final java.util.List<MessagePart> parts = new java.util.ArrayList<>();
        private final java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        private final java.util.Date timestamp = new java.util.Date();

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public void setAddress(String address) {
            this.address = address == null ? "" : address;
        }

        @Override
        public java.util.Date getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean addAddress(String type, String address) {
            this.address = address;
            return true;
        }

        @Override
        public void addMessagePart(MessagePart part) {
            parts.add(part);
        }

        @Override
        public String getAddress(int index) {
            return address;
        }

        @Override
        public String[] getAddresses(String type) {
            return new String[]{address};
        }

        @Override
        public String getHeader(String headerField) {
            return headers.get(headerField);
        }

        @Override
        public int getMessagePartCount() {
            return parts.size();
        }

        @Override
        public MessagePart getMessagePart(int index) {
            return parts.get(index);
        }

        @Override
        public MessagePart[] getMessageParts() {
            return parts.toArray(new MessagePart[0]);
        }

        @Override
        public String getMessagePartContentType(int index) {
            return null;
        }

        @Override
        public String getStartContentId() {
            return headers.get("Start");
        }

        @Override
        public String getSubject() {
            return headers.get("Subject");
        }

        @Override
        public boolean removeAddress(String type, String address) {
            return false;
        }

        @Override
        public void removeAddresses() {
        }

        @Override
        public void removeAddresses(String type) {
        }

        @Override
        public boolean removeMessagePart(MessagePart part) {
            return parts.remove(part);
        }

        @Override
        public boolean removeMessagePartId(String contentId) {
            return false;
        }

        @Override
        public boolean removeMessagePartLocation(String contentLocation) {
            return false;
        }

        @Override
        public void setHeader(String headerField, String headerValue) {
            headers.put(headerField, headerValue);
        }

        @Override
        public void setMessagePartContentType(int index, String contentType) {
        }

        @Override
        public void setStartContentId(String startContentId) {
            headers.put("Start", startContentId);
        }

        @Override
        public void setSubject(String subject) {
            headers.put("Subject", subject);
        }
    }

    /** Connection that creates messages and never receives any. */
    public static final class Connection implements MessageConnection {
        private final String address;
        private MessageListener listener;
        private boolean open = true;

        public Connection(String address) {
            this.address = address;
        }

        @Override
        public Message newMessage(String type) {
            return create(type);
        }

        @Override
        public Message newMessage(String type, String address) {
            Message m = create(type);
            m.setAddress(address);
            return m;
        }

        @Override
        public int numberOfSegments(Message message) {
            if (message instanceof TextMessage) {
                String text = ((TextMessage) message).getPayloadText();
                return text == null || text.isEmpty() ? 0 : (text.length() + 159) / 160;
            }
            return 1;
        }

        @Override
        public Message receive() throws IOException {
            // Nothing can arrive on a desktop host; block-free empty result.
            throw new IOException("no message available");
        }

        @Override
        public void send(Message message) throws IOException {
            if (!open) {
                throw new IOException("connection closed");
            }
            // Delivered nowhere: there is no radio.
        }

        @Override
        public void setMessageListener(MessageListener listener) throws IOException {
            this.listener = listener;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }
    }
}
