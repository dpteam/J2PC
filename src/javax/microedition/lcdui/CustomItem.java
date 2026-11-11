package javax.microedition.lcdui;

/** Application defined item with its own painting. */
public abstract class CustomItem extends Item {

    protected static final int TRAVERSE_HORIZONTAL = 1;
    protected static final int TRAVERSE_VERTICAL = 2;
    protected static final int KEY_PRESS = 4;
    protected static final int KEY_RELEASE = 8;
    protected static final int KEY_REPEAT = 0x10;
    protected static final int POINTER_PRESS = 0x20;
    protected static final int POINTER_RELEASE = 0x40;
    protected static final int POINTER_DRAG = 0x80;
    protected static final int NONE = 0;

    private int width;
    private int height;

    protected CustomItem(String label) {
        super(label);
    }

    protected abstract int getPrefContentWidth(int height);

    protected abstract int getPrefContentHeight(int width);

    protected abstract void paint(Graphics g, int w, int h);

    protected void sizeChanged(int w, int h) {
    }

    protected boolean traverse(int dir, int viewportWidth, int viewportHeight, int[] visRect_inout) {
        return false;
    }

    protected void traverseOut() {
    }

    protected void keyPressed(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    protected final void invalidate() {
        notifyStateChanged();
    }

    protected final int getInteractionModes() {
        return TRAVERSE_HORIZONTAL | TRAVERSE_VERTICAL
                | KEY_PRESS | KEY_RELEASE | KEY_REPEAT
                | POINTER_PRESS | POINTER_RELEASE | POINTER_DRAG;
    }

    @Override
    int height(int width) {
        this.width = width;
        this.height = getPrefContentHeight(width);
        return height;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        int h = height(width);
        g.translate(x, y);
        paint(g, width, h);
        g.translate(-x, -y);
        return h;
    }
}
