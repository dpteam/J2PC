package javax.microedition.lcdui;

import java.awt.image.BufferedImage;

/**
 * MIDP {@code Image}: an immutable bitmap that can be drawn and used as a
 * mutable canvas when created with {@code isMutable = true}.
 */
public class Image {

    public static final int CREATE_ALPHA = 1;

    private final BufferedImage image;
    private final boolean mutable;

    Image(BufferedImage image, boolean mutable) {
        this.image = image;
        this.mutable = mutable;
    }

    public static Image createImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("bad image size");
        }
        return new Image(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), true);
    }

    public static Image createImage(String name) throws java.io.IOException {
        try (java.io.InputStream stream = j2pc.rt.Assets.open(name)) {
            BufferedImage src = javax.imageio.ImageIO.read(stream);
            if (src == null) {
                throw new java.io.IOException("unsupported image: " + name);
            }
            return new Image(toArgb(src), false);
        }
    }

    /** Reads an image from a stream, as defined by MIDP 2.0. */
    public static Image createImage(java.io.InputStream stream) throws java.io.IOException {
        if (stream == null) {
            throw new java.io.IOException("null stream");
        }
        BufferedImage src = javax.imageio.ImageIO.read(stream);
        if (src == null) {
            throw new java.io.IOException("unsupported image stream");
        }
        return new Image(toArgb(src), false);
    }

    /** Opens a resource by name; used by code that reads raw data files. */
    public static java.io.InputStream openResource(String name) throws java.io.IOException {
        return j2pc.rt.Assets.open(name);
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        try {
            BufferedImage src = javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(imageData, imageOffset, imageLength));
            if (src == null) {
                throw new IllegalArgumentException("unsupported image data");
            }
            return new Image(toArgb(src), false);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("unsupported image data: " + e.getMessage());
        }
    }

    /** Creates an image from a region of another image, applying a transform. */
    public static Image createImage(Image source, int x, int y, int width, int height, int transform) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > source.getWidth() || y + height > source.getHeight()) {
            throw new IllegalArgumentException("bad region");
        }
        BufferedImage part = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = part.createGraphics();
        g.drawImage(source.image, -x, -y, null);
        g.dispose();
        return new Image(Graphics.transform(part, transform), false);
    }

    public static Image createImage(Image source) {        BufferedImage copy = new BufferedImage(source.image.getWidth(), source.image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = copy.createGraphics();
        g.drawImage(source.image, 0, 0, null);
        g.dispose();
        return new Image(copy, false);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, rgb, 0, width);
        if (!processAlpha) {
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = rgb[i] | 0xFF000000;
            }
            img.setRGB(0, 0, width, height, rgb, 0, width);
        }
        return new Image(img, false);
    }

    private static BufferedImage toArgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public boolean isMutable() {
        return mutable;
    }

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException("image is not mutable");
        }
        return new Graphics(image.createGraphics());
    }

    public void getRGB(int[] rgbData, int offset, int scanlength,
                       int x, int y, int width, int height) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int px = x + col;
                int py = y + row;
                int value = 0;
                if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                    value = image.getRGB(px, py);
                }
                rgbData[offset + row * scanlength + col] = value;
            }
        }
    }

    BufferedImage buffered() {
        return image;
    }

    /** Wraps a desktop bitmap without copying; used by the game canvas buffer. */
    public static Image wrap(BufferedImage bitmap) {
        return new Image(bitmap, false);
    }
}
