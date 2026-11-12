package j2pc.classfile;

/**
 * JVM constant pool. Entries are stored as raw values so that the reader
 * stays allocation-light:
 *
 * <pre>
 *   UTF8                     -> String
 *   INTEGER                  -> Integer
 *   FLOAT                    -> Float
 *   LONG                     -> Long
 *   DOUBLE                   -> Double
 *   CLASS/STRING/METHOD_TYPE -> Integer (index of the UTF8 entry)
 *   FIELDREF/METHODREF/...   -> int[2] { classIndex, nameAndTypeIndex }
 *   METHOD_HANDLE            -> int[2] { referenceKind, referenceIndex }
 *   DYNAMIC/INVOKE_DYNAMIC   -> int[2] { bootstrapIndex, nameAndTypeIndex }
 * </pre>
 */
public final class ConstantPool {

    public static final int UTF8 = 1;
    public static final int INTEGER = 3;
    public static final int FLOAT = 4;
    public static final int LONG = 5;
    public static final int DOUBLE = 6;
    public static final int CLASS = 7;
    public static final int STRING = 8;
    public static final int FIELDREF = 9;
    public static final int METHODREF = 10;
    public static final int INTERFACE_METHODREF = 11;
    public static final int NAME_AND_TYPE = 12;
    public static final int METHOD_HANDLE = 15;
    public static final int METHOD_TYPE = 16;
    public static final int DYNAMIC = 17;
    public static final int INVOKE_DYNAMIC = 18;
    public static final int MODULE = 19;
    public static final int PACKAGE = 20;

    private final int[] tags;
    private final Object[] values;

    public ConstantPool(int count) {
        this.tags = new int[count];
        this.values = new Object[count];
    }

    public int size() {
        return tags.length;
    }

    public int tag(int index) {
        return index > 0 && index < tags.length ? tags[index] : 0;
    }

    void set(int index, int tag, Object value) {
        tags[index] = tag;
        values[index] = value;
    }

    public String utf8(int index) {
        Object v = value(index);
        return v instanceof String ? (String) v : null;
    }

    public String className(int classIndex) {
        Object v = value(classIndex);
        return v instanceof Integer ? utf8((Integer) v) : null;
    }

    public String stringValue(int stringIndex) {
        Object v = value(stringIndex);
        return v instanceof Integer ? utf8((Integer) v) : null;
    }

    public int intValue(int index) {
        Object v = value(index);
        return v instanceof Integer ? (Integer) v : 0;
    }

    public long longValue(int index) {
        Object v = value(index);
        return v instanceof Long ? (Long) v : 0L;
    }

    public float floatValue(int index) {
        Object v = value(index);
        return v instanceof Float ? (Float) v : 0f;
    }

    public double doubleValue(int index) {
        Object v = value(index);
        return v instanceof Double ? (Double) v : 0d;
    }

    public String refOwner(int index) {
        int[] ref = ref(index);
        return ref == null ? null : className(ref[0]);
    }

    public String refName(int index) {
        int[] ref = ref(index);
        return ref == null ? null : natName(ref[1]);
    }

    public String refType(int index) {
        int[] ref = ref(index);
        return ref == null ? null : natType(ref[1]);
    }

    public int refClassIndex(int index) {
        int[] ref = ref(index);
        return ref == null ? 0 : ref[0];
    }

    public int natIndex(int index) {
        int[] ref = ref(index);
        return ref == null ? 0 : ref[1];
    }

    public String natName(int index) {
        int[] nat = ref(index);
        return nat == null ? null : utf8(nat[0]);
    }

    public String natType(int index) {
        int[] nat = ref(index);
        return nat == null ? null : utf8(nat[1]);
    }

    public int methodHandleKind(int index) {
        int[] ref = ref(index);
        return ref == null ? 0 : ref[0];
    }

    public int methodHandleIndex(int index) {
        int[] ref = ref(index);
        return ref == null ? 0 : ref[1];
    }

    /** Bootstrap method index of a {@code DYNAMIC}/{@code INVOKE_DYNAMIC} entry. */
    public int bootstrapIndex(int dynamicIndex) {
        int[] ref = ref(dynamicIndex);
        return ref == null ? -1 : ref[0];
    }

    /** Descriptor stored in a {@code METHOD_TYPE} entry. */
    public String methodTypeDescriptor(int index) {
        Object v = value(index);
        return v instanceof Integer ? utf8((Integer) v) : null;
    }

    private int[] ref(int index) {
        Object v = value(index);
        return v instanceof int[] ? (int[]) v : null;
    }

    private Object value(int index) {
        return index > 0 && index < values.length ? values[index] : null;
    }
}
