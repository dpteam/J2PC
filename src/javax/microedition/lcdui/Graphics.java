package javax.microedition.lcdui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

/**
 * MIDP {@code Graphics}, implemented on top of {@link Graphics2D}.
 *
 * <p>The coordinate system follows MIDP rules: {@link #translate} shifts the
 * origin, {@link #clipRect} intersects the clip, and drawing is clipped to the
 * target image or the visible part of a canvas.</p>
 */
public final class Graphics implements com.nokia.mid.ui.DirectGraphics {

    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    public static final int SOLID = 0;
    public static final int DOTTED = 1;

    private static final java.util.Map<java.awt.Font, FontMetrics> METRIC_CACHE = new java.util.HashMap<>();

    private final Graphics2D g;
    private final BufferedImage target;

    private int translateX;
    private int translateY;
    private int clipX;
    private int clipY;
    private int clipW;
    private int clipH;
    private boolean hasClip;
    private Font font = Font.getDefaultFont();
    private int strokeStyle = SOLID;

    Graphics(Graphics2D g) {
        this(g, null);
    }

    Graphics(Graphics2D g, BufferedImage target) {
        this.g = g;
        this.target = target;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
    }

    public static Graphics forCanvas(Graphics2D g, BufferedImage target, int width, int height) {
        Graphics gfx = new Graphics(g, target);
        gfx.clipX = 0;
        gfx.clipY = 0;
        gfx.clipW = width;
        gfx.clipH = height;
        gfx.hasClip = true;
        gfx.applyClip();
        return gfx;
    }

    static FontMetrics metricsFor(java.awt.Font f) {
        synchronized (METRIC_CACHE) {
            FontMetrics m = METRIC_CACHE.get(f);
            if (m == null) {
                BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = tmp.createGraphics();
                m = g2.getFontMetrics(f);
                g2.dispose();
                METRIC_CACHE.put(f, m);
            }
            return m;
        }
    }

    // ------------------------------------------------------------- transforms

    public void translate(int x, int y) {
        translateX += x;
        translateY += y;
        applyClip();
    }

    public int getTranslateX() {
        return translateX;
    }

    public int getTranslateY() {
        return translateY;
    }

    public void setClip(int x, int y, int width, int height) {
        clipX = x;
        clipY = y;
        clipW = width;
        clipH = height;
        hasClip = true;
        applyClip();
    }

    public void clipRect(int x, int y, int width, int height) {
        if (!hasClip) {
            setClip(x, y, width, height);
            return;
        }
        int x1 = Math.max(clipX, x);
        int y1 = Math.max(clipY, y);
        int x2 = Math.min(clipX + clipW, x + width);
        int y2 = Math.min(clipY + clipH, y + height);
        clipX = x1;
        clipY = y1;
        clipW = Math.max(0, x2 - x1);
        clipH = Math.max(0, y2 - y1);
        applyClip();
    }

    public int getClipX() {
        return clipX;
    }

    public int getClipY() {
        return clipY;
    }

    public int getClipWidth() {
        return clipW;
    }

    public int getClipHeight() {
        return clipH;
    }

    private void applyClip() {
        AffineTransform old = g.getTransform();
        g.setTransform(new AffineTransform());
        if (hasClip) {
            g.setClip(clipX + translateX, clipY + translateY, clipW, clipH);
        } else {
            g.setClip(null);
        }
        g.setTransform(old);
    }

    // ----------------------------------------------------------------- colors

    public void setColor(int rgb) {
        g.setColor(new Color(rgb & 0xFFFFFF));
    }

    public void setColor(int red, int green, int blue) {
        g.setColor(new Color(clamp(red), clamp(green), clamp(blue)));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public int getColor() {
        return g.getColor().getRGB() & 0xFFFFFF;
    }

    public int getRedComponent() {
        return g.getColor().getRed();
    }

    public int getGreenComponent() {
        return g.getColor().getGreen();
    }

    public int getBlueComponent() {
        return g.getColor().getBlue();
    }

    public void setGrayScale(int value) {
        int v = clamp(value);
        g.setColor(new Color(v, v, v));
    }

    public int getGrayScale() {
        Color c = g.getColor();
        return (c.getRed() + c.getGreen() + c.getBlue()) / 3;
    }

    public void setStrokeStyle(int style) {
        this.strokeStyle = style;
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, style == DOTTED ? new float[]{2f, 2f} : null, 0f));
    }

    public int getStrokeStyle() {
        return strokeStyle;
    }

    // ------------------------------------------------------------------- font

    public void setFont(Font font) {
        if (font == null) {
            throw new NullPointerException("font");
        }
        this.font = font;
        g.setFont(font.awt());
    }

    public Font getFont() {
        return font;
    }

    // ----------------------------------------------------------------- shapes

    public void fillRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fillRect(x + translateX, y + translateY, width, height);
    }

    public void drawRect(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            return;
        }
        g.drawRect(x + translateX, y + translateY, width, height);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        g.drawLine(x1 + translateX, y1 + translateY, x2 + translateX, y2 + translateY);
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        g.drawRoundRect(x + translateX, y + translateY, width, height, arcWidth, arcHeight);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fillRoundRect(x + translateX, y + translateY, width, height, arcWidth, arcHeight);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        g.drawArc(x + translateX, y + translateY, width, height, startAngle, arcAngle);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        g.fillArc(x + translateX, y + translateY, width, height, startAngle, arcAngle);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        fillTriangle(x1, y1, x2, y2, x3, y3, getColor() | 0xFF000000);
    }

    /** Vendor variant with an explicit colour, from the Nokia extension. */
    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor) {
        java.awt.Color saved = g.getColor();
        g.setColor(new java.awt.Color(argbColor & 0xFFFFFF));
        GeneralPath p = new GeneralPath();
        p.moveTo(x1 + translateX, y1 + translateY);
        p.lineTo(x2 + translateX, y2 + translateY);
        p.lineTo(x3 + translateX, y3 + translateY);
        p.closePath();
        g.fill(p);
        g.setColor(saved);
    }

    // ------------------------------------------------------------------ image

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) {
            throw new NullPointerException("image");
        }
        int dx = x;
        int dy = y;
        if ((anchor & HCENTER) != 0) {
            dx -= img.getWidth() / 2;
        } else if ((anchor & RIGHT) != 0) {
            dx -= img.getWidth();
        }
        if ((anchor & VCENTER) != 0) {
            dy -= img.getHeight() / 2;
        } else if ((anchor & BOTTOM) != 0) {
            dy -= img.getHeight();
        }
        g.drawImage(img.buffered(), dx + translateX, dy + translateY, null);
    }

    public void drawRegion(Image src, int xSrc, int ySrc, int width, int height,
                           int transform, int xDest, int yDest, int anchor) {
        BufferedImage part = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = part.createGraphics();
        pg.drawImage(src.buffered(), -xSrc, -ySrc, null);
        pg.dispose();
        BufferedImage out = transform(part, transform);
        int dx = xDest;
        int dy = yDest;
        if ((anchor & HCENTER) != 0) {
            dx -= width / 2;
        } else if ((anchor & RIGHT) != 0) {
            dx -= width;
        }
        if ((anchor & VCENTER) != 0) {
            dy -= height / 2;
        } else if ((anchor & BOTTOM) != 0) {
            dy -= height;
        }
        g.drawImage(out, dx + translateX, dy + translateY, null);
    }

    static BufferedImage transform(BufferedImage src, int transform) {
        if (transform == 0) {
            return src;
        }
        boolean flipH = (transform & 2) != 0 || transform == 4 || transform == 6;
        boolean flipV = (transform & 1) != 0 || transform == 5 || transform == 6;
        boolean rotate = transform >= 4;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = rotate
                ? new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB)
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        AffineTransform at = new AffineTransform();
        if (rotate) {
            at.translate(h, 0);
            at.rotate(Math.PI / 2);
        }
        if (flipH) {
            at.translate(w, 0);
            at.scale(-1, 1);
        }
        if (flipV) {
            at.translate(0, h);
            at.scale(1, -1);
        }
        Graphics2D g2 = out.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(src, at, null);
        g2.dispose();
        return out;
    }

    // ------------------------------------------------------------------- text

    public void drawString(String str, int x, int y, int anchor) {
        if (str == null) {
            throw new NullPointerException("str");
        }
        FontMetrics m = g.getFontMetrics(font.awt());
        int dx = x;
        int dy = y;
        if ((anchor & HCENTER) != 0) {
            dx -= m.stringWidth(str) / 2;
        } else if ((anchor & RIGHT) != 0) {
            dx -= m.stringWidth(str);
        }
        if ((anchor & VCENTER) != 0) {
            dy += (m.getAscent() - m.getDescent()) / 2;
        } else if ((anchor & BOTTOM) != 0) {
            dy -= m.getDescent();
        } else if ((anchor & BASELINE) == 0) {
            dy += m.getAscent();
        }
        g.drawString(str, dx + translateX, dy + translateY);
        if (font.isUnderlined()) {
            int w = m.stringWidth(str);
            int uy = dy + translateY + 1;
            g.drawLine(dx + translateX, uy, dx + translateX + w, uy);
        }
    }

    public void drawChar(char character, int x, int y, int anchor) {
        drawString(String.valueOf(character), x, y, anchor);
    }

    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        drawString(new String(data, offset, length), x, y, anchor);
    }

    public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {
        drawString(str.substring(offset, offset + len), x, y, anchor);
    }

    /**
     * Copies a rectangular block inside the drawing surface.
     *
     * <p>Not part of the MIDP specification, but several J2ME games were built
     * against vendor extensions that provide it, so it is offered here to keep
     * recompiled code working.</p>
     */
    public void copyArea(int xSrc, int ySrc, int width, int height, int xDest, int yDest, int anchor) {
        if (target == null || width <= 0 || height <= 0) {
            return;
        }
        int sx = xSrc + translateX;
        int sy = ySrc + translateY;
        int dx = xDest + translateX;
        int dy = yDest + translateY;
        if ((anchor & HCENTER) != 0) {
            dx -= width / 2;
        } else if ((anchor & RIGHT) != 0) {
            dx -= width;
        }
        if ((anchor & VCENTER) != 0) {
            dy -= height / 2;
        } else if ((anchor & BOTTOM) != 0) {
            dy -= height;
        }
        java.awt.image.BufferedImage copy = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D cg = copy.createGraphics();
        cg.drawImage(target, -sx, -sy, null);
        cg.dispose();
        g.drawImage(copy, dx, dy, null);
    }

    // ------------------------------------------- Nokia DirectGraphics bridge

    private int argbColor = 0xFF000000;

    @Override
    public void drawImage(Image img, int x, int y, int anchor, int manipulation) {
        if (img == null) {
            throw new NullPointerException("image");
        }
        int dx = x;
        int dy = y;
        int w = img.getWidth();
        int h = img.getHeight();
        boolean rotate = (manipulation & (ROTATE_90 | ROTATE_180 | ROTATE_270)) != 0;
        if (rotate) {
            int t = w;
            w = h;
            h = t;
        }
        if ((anchor & HCENTER) != 0) {
            dx -= w / 2;
        } else if ((anchor & RIGHT) != 0) {
            dx -= w;
        }
        if ((anchor & VCENTER) != 0) {
            dy -= h / 2;
        } else if ((anchor & BOTTOM) != 0) {
            dy -= h;
        }
        int transform = 0;
        if ((manipulation & FLIP_HORIZONTAL) != 0) {
            transform |= 2;
        }
        if ((manipulation & FLIP_VERTICAL) != 0) {
            transform |= 1;
        }
        if ((manipulation & ROTATE_90) != 0) {
            transform = 5;
        } else if ((manipulation & ROTATE_180) != 0) {
            transform = 3;
        } else if ((manipulation & ROTATE_270) != 0) {
            transform = 6;
        }
        drawRegion(img, 0, 0, img.getWidth(), img.getHeight(), transform, dx, dy,
                Graphics.TOP | Graphics.LEFT);
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight,
                              int arcThickness) {
        drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    @Override
    public void drawPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints,
                            int argbColor) {
        java.awt.Color saved = g.getColor();
        g.setColor(new java.awt.Color(argbColor & 0xFFFFFF));
        for (int i = 0; i < nPoints; i++) {
            int j = (i + 1) % nPoints;
            g.drawLine(xPoints[xOffset + i] + translateX, yPoints[yOffset + i] + translateY,
                    xPoints[xOffset + j] + translateX, yPoints[yOffset + j] + translateY);
        }
        g.setColor(saved);
    }

    @Override
    public void fillPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints,
                            int argbColor) {
        java.awt.Color saved = g.getColor();
        g.setColor(new java.awt.Color(argbColor & 0xFFFFFF));
        java.awt.Polygon p = new java.awt.Polygon();
        for (int i = 0; i < nPoints; i++) {
            p.addPoint(xPoints[xOffset + i] + translateX, yPoints[yOffset + i] + translateY);
        }
        g.fill(p);
        g.setColor(saved);
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int argbColor) {
        java.awt.Color saved = g.getColor();
        g.setColor(new java.awt.Color(argbColor & 0xFFFFFF));
        drawLine(x1, y1, x2, y2);
        g.setColor(saved);
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int argbColor) {
        java.awt.Color saved = g.getColor();
        g.setColor(new java.awt.Color(argbColor & 0xFFFFFF));
        fillRect(x, y, width, height);
        g.setColor(saved);
    }

    @Override
    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width,
                       int height, int manipulation) {
        if (target == null) {
            return;
        }
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int px = x + col + translateX;
                int py = y + row + translateY;
                int value = 0;
                if (px >= 0 && py >= 0 && px < target.getWidth() && py < target.getHeight()) {
                    value = target.getRGB(px, py);
                }
                rgbData[offset + row * scanlength + col] = value;
            }
        }
    }

    @Override
    public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width,
                        int height, boolean processAlpha) {
        if (width <= 0 || height <= 0) {
            return;
        }
        java.awt.image.BufferedImage tmp = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int value = rgbData[offset + row * scanlength + col];
                if (!processAlpha) {
                    value |= 0xFF000000;
                }
                tmp.setRGB(col, row, value);
            }
        }
        g.drawImage(tmp, x + translateX, y + translateY, null);
    }

    @Override
    public int getAlphaComponent() {
        return (argbColor >>> 24) & 0xFF;
    }

    @Override
    public void setARGBColor(int argbColor) {
        this.argbColor = argbColor;
        setColor(argbColor & 0xFFFFFF);
    }

    @Override
    public int getNativePixelFormat() {
        return TYPE_INT_8888_ARGB;
    }

    @Override
    public Graphics getGraphics() {
        return this;
    }

    @Override
    public int getDisplayColor(int color) {
        return color & 0xFFFFFF;
    }

    @Override
    public void getPixels(int[] dest, int offset, int scanlength, int x, int y, int width,
                          int height, int manipulation) {
        getRGB(dest, offset, scanlength, x, y, width, height, manipulation);
    }

    @Override
    public void getPixels(byte[] dest, int offset, int scanlength, int x, int y, int width,
                          int height, int manipulation) {
        int[] tmp = new int[width * height];
        getRGB(tmp, 0, width, x, y, width, height, manipulation);
        for (int i = 0; i < tmp.length; i++) {
            int v = tmp[i];
            int r = (v >> 16) & 0xFF;
            int g2 = (v >> 8) & 0xFF;
            int b = v & 0xFF;
            // 332 layout, the only byte format the vendor API defines.
            dest[offset + i] = (byte) (((r & 0xE0)) | ((g2 & 0xE0) >> 3) | ((b & 0xC0) >> 6));
        }
    }

    @Override
    public void getPixels(short[] dest, int offset, int scanlength, int x, int y, int width,
                          int height, int manipulation) {
        int[] tmp = new int[width * height];
        getRGB(tmp, 0, width, x, y, width, height, manipulation);
        for (int i = 0; i < tmp.length; i++) {
            int v = tmp[i];
            int r = (v >> 19) & 0x1F;
            int g2 = (v >> 10) & 0x3F;
            int b = (v >> 3) & 0x1F;
            dest[offset + i] = (short) ((r << 11) | (g2 << 5) | b);
        }
    }

    @Override
    public void drawPixels(int[] source, boolean transparency, int offset, int scanlength,
                           int x, int y, int width, int height, int manipulation, int format) {
        int[] rgb = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int v = source[offset + row * scanlength + col];
                rgb[row * width + col] = transparency ? v : (v | 0xFF000000);
            }
        }
        drawRGB(rgb, 0, width, x, y, width, height, transparency);
    }

    @Override
    public void drawPixels(byte[] source, boolean transparency, int offset, int scanlength,
                           int x, int y, int width, int height, int manipulation, int format) {
        int[] rgb = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int v = source[offset + row * scanlength + col] & 0xFF;
                int r = v & 0xE0;
                int g2 = (v << 3) & 0xE0;
                int b = (v << 6) & 0xC0;
                rgb[row * width + col] = 0xFF000000 | (r << 16) | (g2 << 8) | b;
            }
        }
        drawRGB(rgb, 0, width, x, y, width, height, false);
    }

    @Override
    public void drawPixels(short[] source, boolean transparency, int offset, int scanlength,
                           int x, int y, int width, int height, int manipulation, int format) {
        int[] rgb = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int v = source[offset + row * scanlength + col] & 0xFFFF;
                int r = (v >> 11) & 0x1F;
                int g2 = (v >> 5) & 0x3F;
                int b = v & 0x1F;
                rgb[row * width + col] = 0xFF000000
                        | ((r << 3) << 16) | ((g2 << 2) << 8) | (b << 3);
            }
        }
        drawRGB(rgb, 0, width, x, y, width, height, false);
    }

    @Override
    public int blend(int source, int destination, int alpha) {
        int a = alpha & 0xFF;
        int sr = (source >> 16) & 0xFF;
        int sg = (source >> 8) & 0xFF;
        int sb = source & 0xFF;
        int dr = (destination >> 16) & 0xFF;
        int dg = (destination >> 8) & 0xFF;
        int db = destination & 0xFF;
        int r = (sr * a + dr * (255 - a)) / 255;
        int g2 = (sg * a + dg * (255 - a)) / 255;
        int b = (sb * a + db * (255 - a)) / 255;
        return (r << 16) | (g2 << 8) | b;
    }

    /** Creates a fresh copy of this context sharing the same target. */
    Graphics copy() {        Graphics2D g2 = (Graphics2D) g.create();
        Graphics out = new Graphics(g2, target);
        out.translateX = translateX;
        out.translateY = translateY;
        out.clipX = clipX;
        out.clipY = clipY;
        out.clipW = clipW;
        out.clipH = clipH;
        out.hasClip = hasClip;
        out.font = font;
        out.strokeStyle = strokeStyle;
        out.g.setFont(font.awt());
        return out;
    }
}
