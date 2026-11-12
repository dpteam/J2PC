package javax.bluetooth;

/**
 * SDP data element.
 *
 * <p>Fully implemented: it is a value type used for both building and reading
 * service records, and games use it even when Bluetooth is unavailable.</p>
 */
public class DataElement {

    public static final int NULL = 0;
    public static final int U_INT_1 = 8;
    public static final int U_INT_2 = 9;
    public static final int U_INT_4 = 10;
    public static final int U_INT_8 = 11;
    public static final int U_INT_16 = 12;
    public static final int INT_1 = 16;
    public static final int INT_2 = 17;
    public static final int INT_4 = 18;
    public static final int INT_8 = 19;
    public static final int INT_16 = 20;
    public static final int URL = 40;
    public static final int UUID = 24;
    public static final int BOOL = 40 + 8;
    public static final int STRING = 32;
    public static final int DATSEQ = 48;
    public static final int DATALT = 49;

    private final int valueType;
    private final Object value;

    public DataElement(int valueType) {
        this.valueType = valueType;
        this.value = null;
    }

    public DataElement(int valueType, long value) {
        this.valueType = valueType;
        this.value = value;
    }

    public DataElement(int valueType, boolean value) {
        this.valueType = valueType;
        this.value = value;
    }

    public DataElement(int valueType, Object value) {
        this.valueType = valueType;
        this.value = value;
    }

    public void addElement(DataElement elem) {
        if (value instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<DataElement> list = (java.util.List<DataElement>) value;
            list.add(elem);
        }
    }

    public void insertElementAt(DataElement elem, int index) {
        if (value instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<DataElement> list = (java.util.List<DataElement>) value;
            list.add(index, elem);
        }
    }

    public int getDataType() {
        return valueType;
    }

    public int getSize() {
        return value instanceof java.util.List ? ((java.util.List<?>) value).size() : 0;
    }

    public boolean getBoolean() {
        return Boolean.TRUE.equals(value);
    }

    public long getLong() {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public Object getValue() {
        return value;
    }

    public DataElement removeElementAt(int index) {
        if (value instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<DataElement> list = (java.util.List<DataElement>) value;
            return list.remove(index);
        }
        return null;
    }
}
