package javax.microedition.io;

/** HTTPS connection. */
public interface HttpsConnection extends HttpConnection {

    SecurityInfo getSecurityInfo() throws java.io.IOException;
}
