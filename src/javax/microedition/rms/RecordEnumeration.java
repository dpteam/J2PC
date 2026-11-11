package javax.microedition.rms;

/** Iterates over the records of a {@link RecordStore}. */
public interface RecordEnumeration {

    int numRecords();

    byte[] nextRecord() throws InvalidRecordIDException, RecordStoreException;

    int nextRecordId() throws InvalidRecordIDException;

    byte[] previousRecord() throws InvalidRecordIDException, RecordStoreException;

    int previousRecordId() throws InvalidRecordIDException;

    boolean hasNextElement();

    boolean hasPreviousElement();

    void reset();

    void rebuild();

    void keepUpdated(boolean keepUpdated);

    boolean isKeptUpdated();

    void destroy();
}
