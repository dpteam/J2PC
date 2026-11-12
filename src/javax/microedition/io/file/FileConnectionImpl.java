package javax.microedition.io.file;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Vector;

/** {@link FileConnection} backed by {@code java.nio.file}. */
public final class FileConnectionImpl implements FileConnection {

    private final Path path;
    private final String url;
    private boolean open = true;

    public FileConnectionImpl(String url) {
        this.url = url;
        String raw = url;
        if (raw.startsWith("file://")) {
            raw = raw.substring("file://".length());
        }
        if (raw.startsWith("/") && raw.length() > 2 && raw.charAt(2) == ':') {
            // file:///C:/dir -> C:/dir
            raw = raw.substring(1);
        }
        this.path = Path.of(raw.isEmpty() ? "." : raw);
    }

    private void checkOpen() throws IOException {
        if (!open) {
            throw new ConnectionClosedException(url);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        checkOpen();
        return Files.newInputStream(path);
    }

    @Override
    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        checkOpen();
        return Files.newOutputStream(path);
    }

    @Override
    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    @Override
    public OutputStream openOutputStream(long offset) throws IOException {
        checkOpen();
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "rw");
        raf.seek(offset);
        return new java.io.FileOutputStream(raf.getFD());
    }

    @Override
    public long totalSize() throws IOException {
        return Files.getFileStore(path).getTotalSpace();
    }

    @Override
    public long availableSize() throws IOException {
        return Files.getFileStore(path).getUsableSpace();
    }

    @Override
    public long usedSize() throws IOException {
        return totalSize() - availableSize();
    }

    @Override
    public long fileSize() throws IOException {
        return Files.isRegularFile(path) ? Files.size(path) : 0L;
    }

    @Override
    public boolean canRead() {
        return Files.isReadable(path);
    }

    @Override
    public boolean canWrite() {
        return Files.isWritable(path);
    }

    @Override
    public boolean isHidden() throws IOException {
        return Files.isHidden(path);
    }

    @Override
    public void setReadable(boolean readable) {
        path.toFile().setReadable(readable);
    }

    @Override
    public void setWritable(boolean writable) {
        path.toFile().setWritable(writable);
    }

    @Override
    public void setHidden(boolean hidden) {
        // no portable way to set the hidden attribute
    }

    @Override
    public Enumeration<String> list() throws IOException {
        checkOpen();
        Vector<String> names = new Vector<>();
        if (Files.isDirectory(path)) {
            try (java.util.stream.Stream<Path> children = Files.list(path)) {
                children.sorted().forEach(p -> names.add(p.getFileName().toString()));
            }
        }
        return names.elements();
    }

    @Override
    public void create() throws IOException {
        checkOpen();
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        Files.createFile(path);
    }

    @Override
    public void truncate(long byteOffset) throws IOException {
        checkOpen();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "rw")) {
            raf.setLength(byteOffset);
        }
    }

    @Override
    public void delete() throws IOException {
        checkOpen();
        Files.deleteIfExists(path);
    }

    @Override
    public void mkdir() throws IOException {
        checkOpen();
        Files.createDirectories(path);
    }

    @Override
    public void rename(String newName) throws IOException {
        checkOpen();
        Path target = path.resolveSibling(newName);
        Files.move(path, target);
    }

    @Override
    public String getPath() {
        String p = path.toAbsolutePath().toString().replace('\\', '/');
        return p.startsWith("/") ? p : "/" + p;
    }

    @Override
    public String getName() {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    @Override
    public String getURL() {
        return url;
    }

    @Override
    public void close() {
        open = false;
    }
}
