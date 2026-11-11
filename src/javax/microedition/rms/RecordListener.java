package javax.microedition.rms;

/** Callback for record store changes. */
public interface RecordListener {

    void recordAdded(RecordStore store, int recordId);

    void recordChanged(RecordStore store, int recordId);

    void recordDeleted(RecordStore store, int recordId);
}
