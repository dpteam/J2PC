package javax.microedition.rms;

/** Comparator used when enumerating records. */
public interface RecordComparator {

    int EQUIVALENT = 0;
    int PRECEDES = -1;
    int FOLLOWS = 1;

    int compare(byte[] rec1, byte[] rec2);
}
