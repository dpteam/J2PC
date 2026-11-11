package javax.microedition.rms;

/** Thrown when a record store has already been closed. */
public class RecordStoreNotOpenException extends RecordStoreException {

    private static final long serialVersionUID = 1L;

    public RecordStoreNotOpenException() {
    }

    public RecordStoreNotOpenException(String message) {
        super(message);
    }
}
