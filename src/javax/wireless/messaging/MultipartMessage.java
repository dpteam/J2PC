package javax.wireless.messaging;

/** Multipart message. */
public interface MultipartMessage extends Message {

    boolean addAddress(String type, String address);

    void addMessagePart(MessagePart part);

    String getAddress(int index);

    String[] getAddresses(String type);

    String getHeader(String headerField);

    int getMessagePartCount();

    MessagePart getMessagePart(int index);

    MessagePart[] getMessageParts();

    String getMessagePartContentType(int index);

    String getStartContentId();

    String getSubject();

    boolean removeAddress(String type, String address);

    void removeAddresses();

    void removeAddresses(String type);

    boolean removeMessagePart(MessagePart part);

    boolean removeMessagePartId(String contentId);

    boolean removeMessagePartLocation(String contentLocation);

    void setHeader(String headerField, String headerValue);

    void setMessagePartContentType(int index, String contentType);

    void setStartContentId(String startContentId);

    void setSubject(String subject);
}
