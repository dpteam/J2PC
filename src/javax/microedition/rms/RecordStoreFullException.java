package javax.microedition.rms;

/** Thrown when a record store cannot grow any further. */
public class RecordStoreFullException extends RecordStoreException {

    private static final long serialVersionUID = 1L;

    public RecordStoreFullException() {
    }

    public RecordStoreFullException(String message) {
        super(message);
    }
}
