package javax.microedition.rms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * File backed record store.
 *
 * <p>Stores are written to {@code <user.home>/.j2pc/rms/<vendor>/<suite>/}, so
 * that a recompiled game keeps its save data between runs, mirroring the
 * behaviour of a real handset.</p>
 */
public final class RecordStore {

    public static final int AUTHMODE_PRIVATE = 0;
    public static final int AUTHMODE_ANY = 1;

    private static final Map<String, RecordStore> OPEN = new HashMap<>();

    private final String name;
    private final Path file;
    private final TreeMap<Integer, byte[]> records = new TreeMap<>();
    private final List<RecordListener> listeners = new ArrayList<>();
    private int nextId = 1;
    private int version;
    private boolean open = true;

    private RecordStore(String name, Path file) {
        this.name = name;
        this.file = file;
        load();
    }

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNotExist)
            throws RecordStoreException {
        synchronized (OPEN) {
            RecordStore existing = OPEN.get(recordStoreName);
            if (existing != null && existing.open) {
                return existing;
            }
            Path dir = baseDir();
            Path file = dir.resolve(sanitize(recordStoreName) + ".rms");
            if (!Files.exists(file)) {
                if (!createIfNotExist) {
                    throw new RecordStoreNotFoundException(recordStoreName);
                }
                try {
                    Files.createDirectories(dir);
                    Files.write(file, new byte[0]);
                } catch (IOException e) {
                    throw new RecordStoreException("cannot create store: " + e.getMessage());
                }
            }
            RecordStore store = new RecordStore(recordStoreName, file);
            OPEN.put(recordStoreName, store);
            return store;
        }
    }

    public static RecordStore openRecordStore(String name, boolean createIfNotExist,
                                              int authmode, boolean writable) throws RecordStoreException {
        return openRecordStore(name, createIfNotExist);
    }

    public static RecordStore openRecordStore(String name, String vendor, String suite)
            throws RecordStoreException {
        return openRecordStore(name, true);
    }

    public static void deleteRecordStore(String recordStoreName) throws RecordStoreException {
        synchronized (OPEN) {
            RecordStore store = OPEN.remove(recordStoreName);
            Path file = baseDir().resolve(sanitize(recordStoreName) + ".rms");
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new RecordStoreException("cannot delete: " + e.getMessage());
            }
            if (store != null) {
                store.open = false;
            }
        }
    }

    private static Path baseDir() {
        return Path.of(System.getProperty("user.home"), ".j2pc", "rms");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    // ------------------------------------------------------------- management

    public void closeRecordStore() {
        open = false;
        synchronized (OPEN) {
            OPEN.remove(name);
        }
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public int getNumRecords() {
        return records.size();
    }

    public int getSize() {
        int total = 0;
        for (byte[] r : records.values()) {
            total += r.length;
        }
        return total;
    }

    public int getSizeAvailable() {
        return 1024 * 1024;
    }

    public long getLastModified() {
        return System.currentTimeMillis();
    }

    public void addRecordListener(RecordListener listener) {
        listeners.add(listener);
    }

    public void removeRecordListener(RecordListener listener) {
        listeners.remove(listener);
    }

    // ---------------------------------------------------------------- records

    public int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreException {
        int id = nextId++;
        byte[] copy = new byte[numBytes];
        if (numBytes > 0) {
            System.arraycopy(data, offset, copy, 0, numBytes);
        }
        records.put(id, copy);
        persist();
        for (RecordListener l : listeners) {
            l.recordAdded(this, id);
        }
        return id;
    }

    public void deleteRecord(int recordId) throws RecordStoreException {
        if (records.remove(recordId) == null) {
            throw new InvalidRecordIDException("no record " + recordId);
        }
        persist();
        for (RecordListener l : listeners) {
            l.recordDeleted(this, recordId);
        }
    }

    public int getRecordSize(int recordId) throws RecordStoreException {
        byte[] r = records.get(recordId);
        if (r == null) {
            throw new InvalidRecordIDException("no record " + recordId);
        }
        return r.length;
    }

    public int getRecord(int recordId, byte[] buffer, int offset) throws RecordStoreException {
        byte[] r = records.get(recordId);
        if (r == null) {
            throw new InvalidRecordIDException("no record " + recordId);
        }
        int n = Math.min(r.length, buffer.length - offset);
        System.arraycopy(r, 0, buffer, offset, n);
        return n;
    }

    public byte[] getRecord(int recordId) throws RecordStoreException {
        byte[] r = records.get(recordId);
        if (r == null) {
            throw new InvalidRecordIDException("no record " + recordId);
        }
        return r.clone();
    }

    public void setRecord(int recordId, byte[] data, int offset, int numBytes)
            throws RecordStoreException {
        if (!records.containsKey(recordId)) {
            throw new InvalidRecordIDException("no record " + recordId);
        }
        byte[] copy = new byte[numBytes];
        System.arraycopy(data, offset, copy, 0, numBytes);
        records.put(recordId, copy);
        persist();
        for (RecordListener l : listeners) {
            l.recordChanged(this, recordId);
        }
    }

    public int getNextRecordID() {
        return nextId;
    }

    public RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator comparator,
                                              boolean keepUpdated) {
        List<Integer> ids = new ArrayList<>(records.keySet());
        return new SimpleEnumeration(ids, filter, comparator);
    }

    // ------------------------------------------------------------------- i/o

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            byte[] all = Files.readAllBytes(file);
            java.io.DataInputStream in = new java.io.DataInputStream(
                    new java.io.ByteArrayInputStream(all));
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int len = in.readInt();
                byte[] data = new byte[len];
                in.readFully(data);
                records.put(id, data);
                nextId = Math.max(nextId, id + 1);
            }
        } catch (IOException e) {
            // a corrupt store is treated as empty rather than fatal
        }
    }

    private void persist() {
        version++;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(records.size());
            for (Map.Entry<Integer, byte[]> e : records.entrySet()) {
                out.writeInt(e.getKey());
                out.writeInt(e.getValue().length);
                out.write(e.getValue());
            }
            out.flush();
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                fos.write(bos.toByteArray());
            }
        } catch (IOException e) {
            // saving is best effort; the in-memory store stays consistent
        }
    }

    private final class SimpleEnumeration implements RecordEnumeration {

        private final List<Integer> ids;
        private int cursor;

        SimpleEnumeration(List<Integer> ids, RecordFilter filter, RecordComparator comparator) {
            this.ids = new ArrayList<>();
            for (Integer id : ids) {
                if (filter == null || filter.matches(records.get(id))) {
                    this.ids.add(id);
                }
            }
        }

        @Override
        public boolean hasNextElement() {
            return cursor < ids.size();
        }

        @Override
        public boolean hasPreviousElement() {
            return cursor > 0;
        }

        @Override
        public int nextRecordId() throws InvalidRecordIDException {
            if (cursor >= ids.size()) {
                throw new InvalidRecordIDException("no more records");
            }
            return ids.get(cursor++);
        }

        @Override
        public int previousRecordId() throws InvalidRecordIDException {
            if (cursor <= 0) {
                throw new InvalidRecordIDException("no previous record");
            }
            return ids.get(--cursor);
        }

        @Override
        public byte[] nextRecord() throws RecordStoreException {
            return getRecord(nextRecordId());
        }

        @Override
        public byte[] previousRecord() throws RecordStoreException {
            return getRecord(previousRecordId());
        }

        @Override
        public void reset() {
            cursor = 0;
        }

        @Override
        public int numRecords() {
            return ids.size();
        }

        @Override
        public void rebuild() {
        }

        @Override
        public void destroy() {
        }

        @Override
        public void keepUpdated(boolean keepUpdated) {
        }

        @Override
        public boolean isKeptUpdated() {
            return false;
        }
    }
}
