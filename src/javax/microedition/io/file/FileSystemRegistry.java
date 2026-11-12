package javax.microedition.io.file;

import java.util.Enumeration;
import java.util.Vector;

/** Discovery of the file system roots. */
public final class FileSystemRegistry {

    /** Listener for root changes. */
    public interface Listener {

        boolean rootRegistered(String root);

        boolean rootRemoved(String root);
    }

    /** The listener type MIDP actually declares. */
    public static boolean addFileSystemListener(FileSystemListener listener) {
        return true;
    }

    public static boolean removeFileSystemListener(FileSystemListener listener) {
        return true;
    }

    private static final Vector<String> ROOTS = new Vector<>();

    static {
        ROOTS.add("file:///");
    }

    private FileSystemRegistry() {
    }

    public static boolean addFileSystemListener(Listener listener) {
        return true;
    }

    public static boolean removeFileSystemListener(Listener listener) {
        return true;
    }

    public static Enumeration<String> listRoots() {
        return ROOTS.elements();
    }
}
