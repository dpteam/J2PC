package javax.microedition.io.file;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Access to the local file system.
 *
 * <p>Implemented directly on {@code java.nio.file}: a {@code file:///} URL maps
 * onto the real file system, which is exactly what a recompiled application
 * expects when it saves data.</p>
 */
public interface FileConnection extends javax.microedition.io.StreamConnection {

    boolean isOpen();

    /** True when the file or directory exists; part of the MIDP 2.0 API. */
    boolean exists();

    /** True when this connection refers to a directory. */
    boolean isDirectory();

    InputStream openInputStream() throws IOException;

    DataInputStream openDataInputStream() throws IOException;

    OutputStream openOutputStream() throws IOException;

    DataOutputStream openDataOutputStream() throws IOException;

    OutputStream openOutputStream(long offset) throws IOException;

    long totalSize() throws IOException;

    long availableSize() throws IOException;

    long usedSize() throws IOException;

    long fileSize() throws IOException;

    boolean canRead() throws IOException;

    boolean canWrite() throws IOException;

    boolean isHidden() throws IOException;

    void setReadable(boolean readable) throws IOException;

    void setWritable(boolean writable) throws IOException;

    void setHidden(boolean hidden) throws IOException;

    java.util.Enumeration<String> list() throws IOException;

    void create() throws IOException;

    void truncate(long byteOffset) throws IOException;

    void delete() throws IOException;

    void mkdir() throws IOException;

    void rename(String newName) throws IOException;

    String getPath();

    String getName();

    String getURL();

    void close() throws IOException;
}
