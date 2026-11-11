package javax.microedition.rms;

/** Filter used when enumerating records. */
public interface RecordFilter {

    boolean matches(byte[] candidate);
}
