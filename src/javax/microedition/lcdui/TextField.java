package javax.microedition.lcdui;

/** Editable text field. */
public class TextField extends Item {

    public static final int ANY = 0;
    public static final int EMAILADDR = 1;
    public static final int NUMERIC = 2;
    public static final int PHONENUMBER = 3;
    public static final int URL = 4;
    public static final int DECIMAL = 5;

    public static final int PASSWORD = 0x10000;
    public static final int UNEDITABLE = 0x20000;
    public static final int SENSITIVE = 0x40000;
    public static final int NON_PREDICTIVE = 0x80000;
    public static final int INITIAL_CAPS_WORD = 0x100000;
    public static final int INITIAL_CAPS_SENTENCE = 0x200000;

    private String text;
    private final int maxSize;
    private final int constraints;

    public TextField(String label, String text, int maxSize, int constraints) {
        super(label);
        this.text = text == null ? "" : text;
        this.maxSize = maxSize;
        this.constraints = constraints;
    }

    public String getString() {
        return text;
    }

    public void setString(String text) {
        this.text = text == null ? "" : text;
    }

    public int size() {
        return text.length();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getConstraints() {
        return constraints;
    }

    public void setConstraints(int constraints) {
    }

    /** Present in MIDP 2.0; the desktop layer ignores the request. */
    public void setFont(Font font) {
    }

    public void setInitialInputMode(String characterSubset) {
    }

    @Override
    int height(int width) {
        return Font.getDefaultFont().getHeight() + 10;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        Font f = Font.getDefaultFont();
        g.setFont(f);
        g.setColor(0x000000);
        String shown = (constraints & PASSWORD) != 0 ? "*".repeat(text.length()) : text;
        g.drawString(shown, x + 4, y + 3, Graphics.TOP | Graphics.LEFT);
        g.drawRect(x + 2, y + 1, width - 4, f.getHeight() + 4);
        return f.getHeight() + 10;
    }
}
