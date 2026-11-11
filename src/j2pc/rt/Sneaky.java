package j2pc.rt;

/**
 * Rethrows checked exceptions without wrapping them.
 *
 * <p>JVM bytecode has no notion of checked exceptions, so a recompiled method
 * may legally call a method that declares one without a {@code throws} clause.
 * Java source cannot express that, so every generated body is wrapped in a
 * {@code catch (Throwable)} that funnels here. The original exception object is
 * preserved, which keeps the exception tables of the generated code working.</p>
 */
public final class Sneaky {

    private Sneaky() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException sneak(Throwable t) throws T {
        throw (T) t;
    }
}
