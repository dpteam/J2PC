package javax.microedition.io.file;

/** Receives notifications about mounted file system roots. */
public interface FileSystemListener {

    boolean rootChanged(int state, String rootName);
}
