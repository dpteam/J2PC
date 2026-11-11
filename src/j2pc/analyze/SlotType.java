package j2pc.analyze;

/** Abstract value kind tracked by the data-flow analysis. */
public final class SlotType {

    public static final int UNSET = 0;
    public static final int INT = 1;
    public static final int FLOAT = 2;
    public static final int LONG = 3;
    public static final int DOUBLE = 4;
    public static final int REF = 5;
    public static final int RET = 6;
    public static final int CONFLICT = 7;

    public static final SlotType UNSET_T = new SlotType(UNSET, null);
    public static final SlotType INT_T = new SlotType(INT, null);
    public static final SlotType FLOAT_T = new SlotType(FLOAT, null);
    public static final SlotType LONG_T = new SlotType(LONG, null);
    public static final SlotType DOUBLE_T = new SlotType(DOUBLE, null);
    public static final SlotType RET_T = new SlotType(RET, null);
    public static final SlotType CONFLICT_T = new SlotType(CONFLICT, null);
    public static final SlotType NULL_T = new SlotType(REF, null);
    public static final SlotType OBJECT_T = new SlotType(REF, "java/lang/Object");
    public static final SlotType STRING_T = new SlotType(REF, "java/lang/String");
    public static final SlotType THROWABLE_T = new SlotType(REF, "java/lang/Throwable");

    private static final java.util.Map<String, SlotType> REFS = new java.util.HashMap<>();

    public final int kind;
    /** Internal name for references: {@code java/lang/String} or {@code [I}. */
    public final String desc;

    private SlotType(int kind, String desc) {
        this.kind = kind;
        this.desc = desc;
    }

    public static SlotType ref(String internalName) {
        if (internalName == null) {
            return NULL_T;
        }
        SlotType cached = REFS.get(internalName);
        if (cached == null) {
            cached = new SlotType(REF, internalName);
            REFS.put(internalName, cached);
        }
        return cached;
    }

    public static SlotType ofDescriptor(String typeDesc) {
        switch (typeDesc.charAt(0)) {
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
            case 'I':
                return INT_T;
            case 'F':
                return FLOAT_T;
            case 'J':
                return LONG_T;
            case 'D':
                return DOUBLE_T;
            case '[':
                return ref(typeDesc);
            case 'L':
                return ref(typeDesc.substring(1, typeDesc.length() - 1));
            default:
                return OBJECT_T;
        }
    }

    public boolean isCategory2() {
        return kind == LONG || kind == DOUBLE;
    }

    public boolean isRef() {
        return kind == REF;
    }

    public boolean isNull() {
        return kind == REF && desc == null;
    }

    /** Java source type used for the generated variable. */
    public String javaType() {
        switch (kind) {
            case INT:
                return "int";
            case FLOAT:
                return "float";
            case LONG:
                return "long";
            case DOUBLE:
                return "double";
            case REF:
                if (desc == null) {
                    return "java.lang.Object";
                }
                if (desc.startsWith("[")) {
                    return j2pc.classfile.Descriptor.javaType(desc);
                }
                return j2pc.classfile.Descriptor.javaType("L" + desc + ";");
            case RET:
                return "int";
            default:
                return "java.lang.Object";
        }
    }

    /** Descriptor form ({@code Ljava/lang/String;}) or null when unknown. */
    public String descriptor() {
        if (kind != REF) {
            return null;
        }
        if (desc == null) {
            return "Ljava/lang/Object;";
        }
        return desc.startsWith("[") ? desc : "L" + desc + ";";
    }

    public String defaultLiteral() {
        switch (kind) {
            case INT:
            case RET:
                return "0";
            case FLOAT:
                return "0f";
            case LONG:
                return "0L";
            case DOUBLE:
                return "0d";
            default:
                return "null";
        }
    }

    public SlotType merge(SlotType other) {
        if (other == null) {
            return this;
        }
        if (kind == UNSET) {
            return other;
        }
        if (other.kind == UNSET) {
            return this;
        }
        if (kind == other.kind) {
            if (kind != REF) {
                return this;
            }
            if (desc == null) {
                return other;
            }
            if (other.desc == null) {
                return this;
            }
            return desc.equals(other.desc) ? this : OBJECT_T;
        }
        if (kind == CONFLICT || other.kind == CONFLICT) {
            return CONFLICT_T;
        }
        if (kind == REF && other.kind == REF) {
            return OBJECT_T;
        }
        if (kind == RET && other.kind == INT) {
            return CONFLICT_T;
        }
        if (kind == INT && other.kind == RET) {
            return CONFLICT_T;
        }
        return CONFLICT_T;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SlotType)) {
            return false;
        }
        SlotType s = (SlotType) o;
        return kind == s.kind && (desc == null ? s.desc == null : desc.equals(s.desc));
    }

    @Override
    public int hashCode() {
        return kind * 31 + (desc == null ? 0 : desc.hashCode());
    }

    @Override
    public String toString() {
        switch (kind) {
            case INT:
                return "int";
            case FLOAT:
                return "float";
            case LONG:
                return "long";
            case DOUBLE:
                return "double";
            case REF:
                return desc == null ? "null" : desc;
            case RET:
                return "returnaddress";
            case CONFLICT:
                return "<conflict>";
            default:
                return "<unset>";
        }
    }
}
