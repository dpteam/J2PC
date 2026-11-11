package javax.microedition.lcdui;

/** Base class of the prebuilt MIDP screens. */
public abstract class Screen extends Displayable {

    private static final Font TITLE_FONT = Font.create(Font.FACE_PROPORTIONAL,
            Font.STYLE_BOLD, Font.SIZE_SMALL);

    protected Screen() {
    }

    @Override
    protected void paintInternal(Graphics g) {
        int w = g.getClipWidth() > 0 ? g.getClipWidth() : 240;
        int h = g.getClipHeight() > 0 ? g.getClipHeight() : 320;        g.setColor(0xFFFFFF);
        g.fillRect(0, 0, w, h);
        g.setColor(0x000000);
        String title = getTitle();
        if (!title.isEmpty()) {
            g.setFont(TITLE_FONT);
            g.drawString(title, w / 2, 2, Graphics.HCENTER | Graphics.TOP);
            g.drawLine(0, TITLE_FONT.getHeight() + 3, w, TITLE_FONT.getHeight() + 3);
        }
        paintContent(g, TITLE_FONT.getHeight() + 8);
    }

    /** Paints the body of the screen below the title bar. */
    abstract void paintContent(Graphics g, int top);
}
