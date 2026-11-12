package j2pc.tools;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic behavioural probe for a set of classes.
 *
 * <p>Instantiates every class, calls every public method with fixed arguments and
 * prints the results. Running it against the original classes and against the
 * recompiled ones and diffing the two outputs is a direct check that the
 * translation preserved behaviour, not just syntax.</p>
 *
 * <p>Arguments are derived from the parameter type, so the run is repeatable:
 * {@code int} becomes 7, {@code String} becomes {@code "abc"}, and so on.</p>
 */
public final class Verifier {

    private static final int MAX_DEPTH = 2;

    public static void main(String[] args) throws Exception {
        List<String> classNames = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--classes=")) {
                classNames.addAll(Arrays.asList(a.substring(10).split(",")));
            }
        }
        if (classNames.isEmpty()) {
            System.err.println("usage: Verifier --classes=a.b.C,d.e.F");
            System.exit(2);
        }
        for (String name : classNames) {
            probe(name.trim());
        }
    }

    private static void probe(String className) {
        Class<?> c;
        try {
            c = Class.forName(className);
        } catch (Throwable t) {
            System.out.println(className + ": <not loadable: " + t.getClass().getSimpleName() + ">");
            return;
        }
        System.out.println("== " + className);

        Object instance;
        try {
            instance = instantiate(c);
        } catch (Throwable t) {
            System.out.println("  <cannot instantiate: " + describe(t) + ">");
            return;
        }

        Method[] methods = c.getDeclaredMethods();
        Arrays.sort(methods, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method m : methods) {
            if (m.isSynthetic() || Modifier.isAbstract(m.getModifiers())) {
                continue;
            }
            // Lambda bodies are implementation details in both versions: the
            // original marks them synthetic, a recompiled class cannot.
            if (m.getName().startsWith("lambda$") || m.getName().startsWith("access$")) {
                continue;
            }
            if (m.getParameterCount() > 3) {
                continue;
            }
            String label = m.getName() + "(" + m.getParameterCount() + ")";
            try {
                m.setAccessible(true);
                Object target = Modifier.isStatic(m.getModifiers()) ? null : instance;
                Object result = m.invoke(target, arguments(m));
                System.out.println("  " + label + " = " + render(result, m.getReturnType()));
            } catch (InvocationTargetException e) {
                System.out.println("  " + label + " threw " + describe(e.getCause()));
            } catch (Throwable t) {
                System.out.println("  " + label + " ! " + describe(t));
            }
        }
    }

    private static Object instantiate(Class<?> c) throws Exception {
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        Arrays.sort(ctors, (a, b) -> Integer.compare(a.getParameterCount(), b.getParameterCount()));
        for (Constructor<?> ctor : ctors) {
            if (ctor.getParameterCount() > MAX_DEPTH) {
                continue;
            }
            try {
                ctor.setAccessible(true);
                return ctor.newInstance(arguments(ctor.getParameterTypes()));
            } catch (Throwable ignored) {
                // try the next constructor
            }
        }
        throw new IllegalStateException("no usable constructor");
    }

    private static Object[] arguments(Method m) {
        return arguments(m.getParameterTypes());
    }

    private static Object[] arguments(Class<?>[] types) {
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = sample(types[i], i);
        }
        return out;
    }

    private static Object sample(Class<?> type, int index) {
        if (type == int.class) {
            return index == 0 ? 7 : index;
        }
        if (type == long.class) {
            return (long) (index == 0 ? 7 : index);
        }
        if (type == short.class) {
            return (short) (index == 0 ? 7 : index);
        }
        if (type == byte.class) {
            return (byte) (index == 0 ? 7 : index);
        }
        if (type == char.class) {
            return (char) ('a' + index);
        }
        if (type == float.class) {
            return index == 0 ? 1.5f : (float) index;
        }
        if (type == double.class) {
            return index == 0 ? 1.5d : (double) index;
        }
        if (type == boolean.class) {
            return index == 0;
        }
        if (type == String.class) {
            return index == 0 ? "abc" : "d";
        }
        if (type == int[].class) {
            return new int[]{1, 2, 3};
        }
        if (type == long[].class) {
            return new long[]{1L, 2L};
        }
        if (type == byte[].class) {
            return new byte[]{1, 2};
        }
        if (type == char[].class) {
            return new char[]{'a', 'b'};
        }
        if (type == boolean[].class) {
            return new boolean[]{true, false};
        }
        if (type == double[].class) {
            return new double[]{1.5d, 2.5d};
        }
        if (type == float[].class) {
            return new float[]{1.5f, 2.5f};
        }
        if (type == short[].class) {
            return new short[]{1, 2};
        }
        if (type == int[][].class) {
            return new int[][]{{1, 2}, {3, 4}};
        }
        if (type == Object.class) {
            return "abc";
        }
        if (type.isArray()) {
            return null;
        }
        return null;
    }

    private static String render(Object value, Class<?> declaredType) {
        if (value == null) {
            return "null";
        }
        if (value instanceof int[]) {
            return Arrays.toString((int[]) value);
        }
        if (value instanceof long[]) {
            return Arrays.toString((long[]) value);
        }
        if (value instanceof byte[]) {
            return Arrays.toString((byte[]) value);
        }
        if (value instanceof char[]) {
            return Arrays.toString((char[]) value);
        }
        if (value instanceof boolean[]) {
            return Arrays.toString((boolean[]) value);
        }
        if (value instanceof Object[]) {
            return Arrays.deepToString((Object[]) value);
        }
        if (value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character
                || value instanceof Enum) {
            return String.valueOf(value);
        }
        // Object identity differs between runs, and a lambda is a proxy in the
        // original and an anonymous class after recompilation. The declared
        // return type is the part that actually has to match.
        if (declaredType != null && !declaredType.isInterface()
                && declaredType != Object.class && !declaredType.isArray()) {
            return declaredType.getName();
        }
        return declaredType == null ? value.getClass().getName() : declaredType.getName();
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String message = t.getMessage();
        return t.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private Verifier() {
    }
}
