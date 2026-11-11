package javax.microedition.rms;

/** Thrown when a record id is out of range. */
public class InvalidRecordIDException extends RecordStoreException {

    private static final long serialVersionUID = 1L;

    public InvalidRecordIDException() {
    }

    public InvalidRecordIDException(String message) {
        super(message);
    }
}
