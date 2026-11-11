package javax.microedition.io;

/** Connection that carries a MIME type and a content length. */
public interface ContentConnection extends StreamConnection {

    String getType();

    String getEncoding();

    long getLength();
}
