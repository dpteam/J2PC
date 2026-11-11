package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Animated, transformable bitmap. */
public class Sprite extends Layer {

    public static final int TRANS_NONE = 0;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_MIRROR_ROT90 = 7;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR_ROT270 = 4;

    private final Image source;
    private final int frameWidth;
    private final int frameHeight;
    private int frame;
    private int transform;
    private int refX;
    private int refY;
    private int rawFrame;

    public Sprite(Image image) {
        this(image, image.getWidth(), image.getHeight());
    }

    public Sprite(Image image, int frameWidth, int frameHeight) {
        super(frameWidth, frameHeight);
        if (frameWidth <= 0 || frameHeight <= 0
                || image.getWidth() % frameWidth != 0 || image.getHeight() % frameHeight != 0) {
            throw new IllegalArgumentException("bad frame size");
        }
        this.source = image;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public Sprite(Sprite s) {
        super(s.getWidth(), s.getHeight());
        this.source = s.source;
        this.frameWidth = s.frameWidth;
        this.frameHeight = s.frameHeight;
        this.frame = s.frame;
        this.transform = s.transform;
        this.refX = s.refX;
        this.refY = s.refY;
        this.x = s.x;
        this.y = s.y;
    }

    public void defineReferencePixel(int x, int y) {
        this.refX = x;
        this.refY = y;
    }

    public int getRefPixelX() {
        return x + refX;
    }

    public int getRefPixelY() {
        return y + refY;
    }

    public void setRefPixelPosition(int x, int y) {
        this.x = x - refX;
        this.y = y - refY;
    }

    public int getFrame() {
        return frame;
    }

    public void setFrame(int sequenceIndex) {
        this.frame = sequenceIndex;
    }

    public int getRawFrameCount() {
        int cols = source.getWidth() / frameWidth;
        int rows = source.getHeight() / frameHeight;
        return cols * rows;
    }

    public int getFrameSequenceLength() {
        return getRawFrameCount();
    }

    public void nextFrame() {
        frame = (frame + 1) % getFrameSequenceLength();
    }

    public void prevFrame() {
        frame = (frame - 1 + getFrameSequenceLength()) % getFrameSequenceLength();
    }

    public void setFrameSequence(int[] sequence) {
        frame = 0;
    }

    public void setTransform(int transform) {
        this.transform = transform;
    }

    public int getTransform() {
        return transform;
    }

    @Override
    public void paint(Graphics g) {
        if (!visible) {
            return;
        }
        int cols = source.getWidth() / frameWidth;
        int sx = (frame % cols) * frameWidth;
        int sy = (frame / cols) * frameHeight;
        g.drawRegion(source, sx, sy, frameWidth, frameHeight, transform, x, y,
                Graphics.TOP | Graphics.LEFT);
    }
}
