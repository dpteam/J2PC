package javax.microedition.lcdui;

/** Plain text item. */
public class StringItem extends Item {

    public static final int PLAIN = 0;
    public static final int HYPERLINK = 1;
    public static final int BUTTON = 2;

    private String text;
    private int appearanceMode;

    public StringItem(String label, String text) {
        this(label, text, PLAIN);
    }

    public StringItem(String label, String text, int appearanceMode) {
        super(label);
        this.text = text == null ? "" : text;
        this.appearanceMode = appearanceMode;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public int getAppearanceMode() {
        return appearanceMode;
    }

    @Override
    int height(int width) {
        return Font.getDefaultFont().getHeight() + 4;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        Font f = Font.getDefaultFont();
        g.setFont(f);
        g.setColor(0x000000);
        g.drawString(text, x + 2, y + 2, Graphics.TOP | Graphics.LEFT);
        return f.getHeight() + 4;
    }
}
