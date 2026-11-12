package javax.microedition.io.file;

/** Receives notifications about mounted file system roots. */
public interface FileSystemListener {

    /**
     * JSR-75 declares this {@code void}. Returning {@code boolean} would make every
     * suite that implements the interface fail to compile: a method with a
     * different return type is a different signature, so it does not override.
     */
    void rootChanged(int state, String rootName);
}
