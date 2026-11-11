package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Base class of the sprites and tile layers managed by a {@link LayerManager}. */
public abstract class Layer {

    int x;
    int y;
    int width;
    int height;
    boolean visible = true;

    Layer(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void move(int dx, int dy) {
        this.x += dx;
        this.y += dy;
    }

    public final int getX() {
        return x;
    }

    public final int getY() {
        return y;
    }

    public final int getWidth() {
        return width;
    }

    public final int getHeight() {
        return height;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public final boolean isVisible() {
        return visible;
    }

    public void setLayerManager(LayerManager manager) {
        this.manager = manager;
    }

    LayerManager manager;

    public abstract void paint(Graphics g);

    /** Collision test against another layer, honouring the current transforms. */
    public final boolean collidesWith(Layer other, boolean pixelLevel) {
        return collidesWith(other, 0, 0, pixelLevel);
    }

    public final boolean collidesWith(Layer other, int x, int y, boolean pixelLevel) {
        if (!visible || !other.visible) {
            return false;
        }
        int ax1 = this.x;
        int ay1 = this.y;
        int ax2 = ax1 + width;
        int ay2 = ay1 + height;
        int bx1 = other.x + x;
        int by1 = other.y + y;
        int bx2 = bx1 + other.width;
        int by2 = by1 + other.height;
        return ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2;
    }

    public final boolean collidesWith(TiledLayer layer, boolean pixelLevel) {
        return collidesWith((Layer) layer, pixelLevel);
    }

    public final boolean collidesWith(Sprite sprite, boolean pixelLevel) {
        return collidesWith((Layer) sprite, pixelLevel);
    }

    public final boolean collidesWith(Image image, int x, int y, boolean pixelLevel) {
        int ax1 = this.x;
        int ay1 = this.y;
        int ax2 = ax1 + width;
        int ay2 = ay1 + height;
        int bx1 = x;
        int by1 = y;
        int bx2 = bx1 + image.getWidth();
        int by2 = by1 + image.getHeight();
        return ax1 < bx2 && bx1 < ax2 && ay1 < by2 && by1 < ay2;
    }
}
