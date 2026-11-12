package javax.microedition.lcdui;

/** Full screen text editor. */
public class TextBox extends Screen {

    private final TextField field;

    public TextBox(String title, String text, int maxSize, int constraints) {
        setTitleInternal(title);
        this.field = new TextField(null, text, maxSize, constraints);
    }

    public String getString() {
        return field.getString();
    }

    public void setString(String text) {
        field.setString(text);
    }

    public int size() {
        return field.size();
    }

    public int getMaxSize() {
        return field.getMaxSize();
    }

    /**
     * Sets the buffer limit and returns the previous one.
     *
     * <p>MIDP declares this {@code void}, but several vendor builds — and the
     * suites compiled against them — use an {@code int} return. Java lets a caller
     * discard a result, so returning a value compiles for both: code written
     * against the {@code void} signature ignores it, code that stores it gets the
     * old limit.</p>
     */
    public int setMaxSize(int maxSize) {
        int previous = field.getMaxSize();
        field.setMaxSize(maxSize);
        return previous;
    }

    public int getConstraints() {
        return field.getConstraints();
    }

    public void setConstraints(int constraints) {
        field.setConstraints(constraints);
    }

    public void setInitialInputMode(String characterSubset) {
        field.setInitialInputMode(characterSubset);
    }

    @Override
    void paintContent(Graphics g, int top) {
        field.paint(g, 0, top, g.getClipWidth());
    }
}
