package javax.microedition.rms;

/** Base class of the record store failures. */
public class RecordStoreException extends Exception {

    private static final long serialVersionUID = 1L;

    public RecordStoreException() {
        super();
    }

    public RecordStoreException(String message) {
        super(message);
    }
}
