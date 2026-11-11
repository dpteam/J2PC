package j2pc.rt;

import java.lang.ref.WeakReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Emulation of JVM monitors for recompiled code.
 *
 * <p>Java source has no {@code monitorenter}/{@code monitorexit} instructions,
 * so the recompiler lowers them to these helpers. A per-object reentrant lock
 * reproduces the observable semantics (mutual exclusion and reentrancy).</p>
 *
 * <p>The association must be by <em>identity</em>: a {@code Vector} or any other
 * collection has content-based {@code equals}/{@code hashCode}, so a hash map
 * would hand out a different lock after the collection changed and
 * {@code monitorexit} would fail. Keys are weak references so that locked
 * objects can still be collected.</p>
 */
public final class Monitor {

    private static final Entry[] TABLE = new Entry[64];

    private Monitor() {
    }

    public static void enter(Object o) {
        if (o == null) {
            throw new NullPointerException("monitorenter on null");
        }
        lockFor(o).lock();
    }

    public static void exit(Object o) {
        if (o == null) {
            throw new NullPointerException("monitorexit on null");
        }
        ReentrantLock lock = find(o, false);
        if (lock == null || !lock.isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException("monitorexit without monitorenter");
        }
        lock.unlock();
    }

    private static synchronized ReentrantLock lockFor(Object o) {
        ReentrantLock existing = find(o, false);
        if (existing != null) {
            return existing;
        }
        ReentrantLock lock = new ReentrantLock();
        int slot = (System.identityHashCode(o) & 0x7FFFFFFF) % TABLE.length;
        TABLE[slot] = new Entry(new WeakReference<>(o), lock, TABLE[slot]);
        return lock;
    }

    /** Finds the lock for {@code o}, dropping entries whose key was collected. */
    private static ReentrantLock find(Object o, boolean purge) {
        int slot = (System.identityHashCode(o) & 0x7FFFFFFF) % TABLE.length;
        Entry prev = null;
        Entry e = TABLE[slot];
        while (e != null) {
            Object key = e.key.get();
            Entry next = e.next;
            if (key == null) {
                if (prev == null) {
                    TABLE[slot] = next;
                } else {
                    prev.next = next;
                }
            } else if (key == o) {
                return e.lock;
            } else {
                prev = e;
            }
            e = next;
        }
        return null;
    }

    private static final class Entry {
        final WeakReference<Object> key;
        final ReentrantLock lock;
        Entry next;

        Entry(WeakReference<Object> key, ReentrantLock lock, Entry next) {
            this.key = key;
            this.lock = lock;
            this.next = next;
        }
    }
}
