package j2pc.rt;

import java.lang.reflect.Array;

/**
 * Array helpers for cases where the recompiler cannot prove the array type.
 *
 * <p>Bytecode carries no array element types, and obfuscated code happily reuses
 * one local slot for unrelated arrays. When the data-flow analysis cannot pin a
 * type down, {@code arraylength} is lowered to a reflective call instead of a
 * cast that might fail at run time.</p>
 */
public final class Arrays {

    private Arrays() {
    }

    public static int length(Object array) {
        if (array == null) {
            throw new NullPointerException("arraylength on null");
        }
        return Array.getLength(array);
    }

    public static Object get(Object array, int index) {
        if (array == null) {
            throw new NullPointerException("array load on null");
        }
        return Array.get(array, index);
    }

    /** Reflective store, used when the element type could not be proven. */
    public static void set(Object array, int index, Object value) {
        if (array == null) {
            throw new NullPointerException("array store on null");
        }
        Array.set(array, index, value);
    }
}
