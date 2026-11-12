package javax.microedition.lcdui;

/** Item that displays an image. */
public class ImageItem extends Item {

    public static final int LAYOUT_DEFAULT = 0;
    public static final int LAYOUT_LEFT = 1;
    public static final int LAYOUT_RIGHT = 2;
    public static final int LAYOUT_CENTER = 3;
    public static final int LAYOUT_NEWLINE_BEFORE = 0x100;
    public static final int LAYOUT_NEWLINE_AFTER = 0x200;

    private Image image;
    private String altText;

    public ImageItem(String label, Image image, int layout, String altText) {
        super(label);
        this.image = image;
        this.altText = altText;
        setLayout(layout);
    }

    /**
     * Five-argument form used by some vendor builds and by MIDP 3.0.
     *
     * <p>The extra parameter selects an appearance mode that the desktop host does
     * not model; accepting and ignoring it keeps suites compiled against those
     * variants working.</p>
     */
    public ImageItem(String label, Image image, int layout, String altText, int appearanceMode) {
        this(label, image, layout, altText);
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    @Override
    int height(int width) {
        return image == null ? Font.getDefaultFont().getHeight() : image.getHeight() + 4;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        if (image != null) {
            int dx = x + 2;
            int layout = getLayout() & 3;
            if (layout == LAYOUT_CENTER) {
                dx = x + (width - image.getWidth()) / 2;
            } else if (layout == LAYOUT_RIGHT) {
                dx = x + width - image.getWidth() - 2;
            }
            g.drawImage(image, dx, y + 2, Graphics.TOP | Graphics.LEFT);
            return image.getHeight() + 4;
        }
        g.setFont(Font.getDefaultFont());
        g.drawString(altText == null ? "" : altText, x + 2, y + 2, Graphics.TOP | Graphics.LEFT);
        return Font.getDefaultFont().getHeight() + 4;
    }
}
