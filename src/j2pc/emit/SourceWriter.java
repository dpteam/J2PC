package j2pc.emit;

/** Indentation-aware string builder for generated Java sources. */
public final class SourceWriter {

    private final StringBuilder sb = new StringBuilder(8192);
    private final String unit;
    private int indent;
    private java.util.List<String> sink;

    public SourceWriter(String unit) {
        this.unit = unit;
    }

    /**
     * Redirects subsequent lines into {@code target} instead of the buffer.
     * Used to collect a region that has to be reordered before being written,
     * such as the mandatory {@code super(...)} call of a constructor.
     */
    public void captureTo(java.util.List<String> target) {
        this.sink = target;
    }

    /** Stops capturing and returns to normal output. */
    public void stopCapture() {
        this.sink = null;
    }

    public void line(String text) {
        if (sink != null) {
            sink.add(text);
            return;
        }
        for (int i = 0; i < indent; i++) {
            sb.append(unit);
        }
        sb.append(text).append('\n');
    }

    public void blank() {
        sb.append('\n');
    }

    public void indent() {
        indent++;
    }

    public void outdent() {
        if (indent > 0) {
            indent--;
        }
    }

    public int depth() {
        return indent;
    }

    /** Returns the accumulated text and clears the buffer. */
    public String drain() {
        String text = sb.toString();
        sb.setLength(0);
        return text;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
