package javax.microedition.rms;

/** Thrown when a record store does not exist. */
public class RecordStoreNotFoundException extends RecordStoreException {

    private static final long serialVersionUID = 1L;

    public RecordStoreNotFoundException() {
    }

    public RecordStoreNotFoundException(String message) {
        super(message);
    }
}
