package javax.microedition.lcdui;

/** Modal message screen. */
public class Alert extends Screen {

    public static final int FOREVER = -2;
    public static final int DISMISS_COMMAND = 0;
    public static final int ALERT_DEFAULT = 0;
    public static final int ALERT_INFO = 1;
    public static final int ALERT_WARNING = 2;
    public static final int ALERT_ERROR = 3;
    public static final int ALERT_ALARM = 4;

    private String string;
    private Image image;
    private int timeout;
    private int type;

    public Alert(String title) {
        setTitleInternal(title);
    }

    public Alert(String title, String alertText, Image alertImage, int alertType) {
        setTitleInternal(title);
        this.string = alertText;
        this.image = alertImage;
        this.type = alertType;
    }

    public String getString() {
        return string;
    }

    public void setString(String str) {
        this.string = str;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image img) {
        this.image = img;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int time) {
        this.timeout = time;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Gauge getIndicator() {
        return null;
    }

    @Override
    void paintContent(Graphics g, int top) {
        Font f = Font.getDefaultFont();
        g.setFont(f);
        g.setColor(0x000000);
        if (string != null) {
            g.drawString(string, g.getClipWidth() / 2, top + 20,
                    Graphics.HCENTER | Graphics.TOP);
        }
    }
}
