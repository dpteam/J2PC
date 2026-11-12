package javax.wireless.messaging;

/** A part of a multipart message. */
public interface MessagePart {

    String getContentID();

    String getContentLocation();

    String getEncoding();

    byte[] getContent();

    int getLength();

    void setContentID(String contentID);

    void setContentLocation(String contentLocation);

    void setContent(byte[] content, String contentType, String contentID,
                    String contentLocation, String encoding);

    void setEncoding(String encoding);
}
