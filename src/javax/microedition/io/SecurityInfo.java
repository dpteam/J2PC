package javax.microedition.io;

/** Security information of an HTTPS connection. */
public interface SecurityInfo {

    String getProtocolName();

    String getProtocolVersion();

    String getCipherSuite();

    java.security.cert.Certificate getServerCertificate();
}
