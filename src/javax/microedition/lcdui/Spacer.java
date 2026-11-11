package javax.microedition.lcdui;

/** Empty space. */
public class Spacer extends Item {

    private final int minWidth;
    private final int minHeight;

    public Spacer(int minWidth, int minHeight) {
        super(null);
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    @Override
    public void setLabel(String label) {
        throw new IllegalStateException("Spacer has no label");
    }

    @Override
    public int getMinimumWidth() {
        return minWidth;
    }

    @Override
    public int getMinimumHeight() {
        return minHeight;
    }

    @Override
    int height(int width) {
        return minHeight;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        return minHeight;
    }
}
