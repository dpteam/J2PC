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

    /**
     * MIDP 2.0 overload that takes an {@link AlertType} constant.
     *
     * <p>Bytecode compiled against the newer signature calls this one; without it
     * the generated source does not compile even though both forms describe the
     * same alert.</p>
     */
    public Alert(String title, String alertText, Image alertImage, AlertType alertType) {
        this(title, alertText, alertImage, typeCode(alertType));
    }

    private static int typeCode(AlertType alertType) {
        if (alertType == null || alertType == AlertType.INFO) {
            return ALERT_INFO;
        }
        if (alertType == AlertType.WARNING) {
            return ALERT_WARNING;
        }
        if (alertType == AlertType.ERROR) {
            return ALERT_ERROR;
        }
        if (alertType == AlertType.ALARM) {
            return ALERT_ALARM;
        }
        return ALERT_DEFAULT;
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

    /** MIDP 2.0 overload taking an {@link AlertType} constant. */
    public void setType(AlertType type) {
        this.type = typeCode(type);
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
