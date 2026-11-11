package j2pc.classfile;

import java.util.ArrayList;
import java.util.List;

/** Helpers for JVM type descriptors: {@code (ILjava/lang/String;)V}. */
public final class Descriptor {

    private Descriptor() {
    }

    /** Splits a method descriptor into its argument type descriptors. */
    public static String[] args(String methodDesc) {
        if (methodDesc == null || methodDesc.isEmpty() || methodDesc.charAt(0) != '(') {
            return new String[0];
        }
        List<String> out = new ArrayList<>();
        int i = 1;
        while (i < methodDesc.length() && methodDesc.charAt(i) != ')') {
            int start = i;
            while (methodDesc.charAt(i) == '[') {
                i++;
            }
            if (methodDesc.charAt(i) == 'L') {
                i = methodDesc.indexOf(';', i);
                if (i < 0) {
                    throw new IllegalArgumentException("bad descriptor: " + methodDesc);
                }
                i++;
            } else {
                i++;
            }
            out.add(methodDesc.substring(start, i));
        }
        return out.toArray(new String[0]);
    }

    public static String returnType(String methodDesc) {
        int i = methodDesc.indexOf(')');
        if (i < 0) {
            return "V";
        }
        return methodDesc.substring(i + 1);
    }

    /** Number of local slots a value of this type occupies (1 or 2). */
    public static int slots(String typeDesc) {
        char c = typeDesc.charAt(0);
        return (c == 'J' || c == 'D') ? 2 : (c == 'V' ? 0 : 1);
    }

    public static int argSlots(String methodDesc) {
        int n = 0;
        for (String a : args(methodDesc)) {
            n += slots(a);
        }
        return n;
    }

    public static boolean isPrimitive(String typeDesc) {
        return typeDesc.length() == 1 && "VZBCSIJFD".indexOf(typeDesc.charAt(0)) >= 0;
    }

    public static boolean isArray(String typeDesc) {
        return !typeDesc.isEmpty() && typeDesc.charAt(0) == '[';
    }

    /** {@code [I -> I}, {@code [[Ljava/lang/String; -> [Ljava/lang/String;}. */
    public static String elementType(String arrayDesc) {
        return arrayDesc.substring(1);
    }

    /** Converts a JVM descriptor into a Java source type, fully qualified. */
    public static String javaType(String typeDesc) {
        if (typeDesc == null || typeDesc.isEmpty()) {
            return "void";
        }
        switch (typeDesc.charAt(0)) {
            case 'V':
                return "void";
            case 'Z':
                return "boolean";
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'S':
                return "short";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'F':
                return "float";
            case 'D':
                return "double";
            case '[':
                return javaType(elementType(typeDesc)) + "[]";
            case 'L':
                return typeDesc.substring(1, typeDesc.length() - 1).replace('/', '.').replace('$', '$');
            default:
                return "java.lang.Object";
        }
    }

    /** Boxed Java type used when a primitive has to live in an Object slot. */
    public static String boxedType(String typeDesc) {
        switch (typeDesc.charAt(0)) {
            case 'Z':
                return "java.lang.Boolean";
            case 'B':
                return "java.lang.Byte";
            case 'C':
                return "java.lang.Character";
            case 'S':
                return "java.lang.Short";
            case 'I':
                return "java.lang.Integer";
            case 'J':
                return "java.lang.Long";
            case 'F':
                return "java.lang.Float";
            case 'D':
                return "java.lang.Double";
            default:
                return javaType(typeDesc);
        }
    }

    public static String internalName(String typeDesc) {
        if (isArray(typeDesc)) {
            return typeDesc;
        }
        if (typeDesc.startsWith("L")) {
            return typeDesc.substring(1, typeDesc.length() - 1);
        }
        return typeDesc;
    }
}
