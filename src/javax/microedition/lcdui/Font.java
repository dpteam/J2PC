package javax.microedition.lcdui;

import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** MIDP font. Backed by an AWT font of a comparable size. */
public final class Font {

    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    public static final int FONT_STATIC_TEXT = 0;
    public static final int FONT_INPUT_TEXT = 1;

    private static Font defaultFont;

    private final java.awt.Font awtFont;
    private final int face;
    private final int style;
    private final int size;

    private Font(java.awt.Font awtFont, int face, int style, int size) {
        this.awtFont = awtFont;
        this.face = face;
        this.style = style;
        this.size = size;
    }

    public static Font getDefaultFont() {
        if (defaultFont == null) {
            defaultFont = create(FACE_PROPORTIONAL, STYLE_PLAIN, SIZE_MEDIUM);
        }
        return defaultFont;
    }

    public static Font getFont(int fontSpecifier) {
        return getDefaultFont();
    }

    /** Convenience overload used by some MIDlet libraries. */
    public static Font getFont(int face, int style, int size) {
        return create(face, style, size);
    }

    public static Font create(int face, int style, int size) {
        String family;
        switch (face) {
            case FACE_MONOSPACE:
                family = java.awt.Font.MONOSPACED;
                break;
            case FACE_SYSTEM:
                family = java.awt.Font.DIALOG;
                break;
            default:
                family = java.awt.Font.SANS_SERIF;
        }
        int awtStyle = java.awt.Font.PLAIN;
        if ((style & STYLE_BOLD) != 0) {
            awtStyle |= java.awt.Font.BOLD;
        }
        if ((style & STYLE_ITALIC) != 0) {
            awtStyle |= java.awt.Font.ITALIC;
        }
        int pt;
        switch (size) {
            case SIZE_SMALL:
                pt = 10;
                break;
            case SIZE_LARGE:
                pt = 18;
                break;
            default:
                pt = 13;
        }
        return new Font(new java.awt.Font(family, awtStyle, pt), face, style, size);
    }

    public int getFace() {
        return face;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public boolean isPlain() {
        return style == STYLE_PLAIN;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public int getHeight() {
        FontMetrics m = metrics();
        return m.getHeight();
    }

    public int getBaselinePosition() {
        return metrics().getAscent();
    }

    public int charWidth(char c) {
        return metrics().charWidth(c);
    }

    public int charsWidth(char[] ch, int offset, int length) {
        return metrics().charsWidth(ch, offset, length);
    }

    public int stringWidth(String s) {
        return metrics().stringWidth(s == null ? "" : s);
    }

    public int substringWidth(String s, int offset, int length) {
        return stringWidth(s.substring(offset, offset + length));
    }

    private FontMetrics metrics() {
        return Graphics.metricsFor(awtFont);
    }

    java.awt.Font awt() {
        return awtFont;
    }

    static FontMetrics metrics(Graphics2D g, Font f) {
        return g.getFontMetrics(f.awtFont);
    }
}
