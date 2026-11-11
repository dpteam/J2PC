package javax.microedition.media;

/** Source of media data. */
public interface DataSource {

    String getContentType();

    java.io.InputStream openStream() throws java.io.IOException;
}
