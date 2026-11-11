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
