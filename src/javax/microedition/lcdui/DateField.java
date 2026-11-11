package javax.microedition.lcdui;

import java.util.Calendar;

/** Date and time editor. */
public class DateField extends Item {

    public static final int DATE = 1;
    public static final int TIME = 2;
    public static final int DATE_TIME = 3;

    private final int mode;
    private long time;
    private final java.util.TimeZone zone;

    public DateField(String label, int mode) {
        this(label, mode, null);
    }

    public DateField(String label, int mode, java.util.TimeZone timeZone) {
        super(label);
        this.mode = mode;
        this.zone = timeZone == null ? java.util.TimeZone.getDefault() : timeZone;
    }

    public java.util.Date getDate() {
        return time == 0 ? null : new java.util.Date(time);
    }

    public void setDate(java.util.Date date) {
        this.time = date == null ? 0 : date.getTime();
    }

    public int getInputMode() {
        return mode;
    }

    @Override
    int height(int width) {
        return Font.getDefaultFont().getHeight() + 8;
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        Font f = Font.getDefaultFont();
        g.setFont(f);
        g.setColor(0x000000);
        Calendar c = Calendar.getInstance(zone);
        if (time != 0) {
            c.setTimeInMillis(time);
        }
        String text;
        if (mode == TIME) {
            text = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        } else {
            text = String.format("%04d-%02d-%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        }
        g.drawString(text, x + 4, y + 4, Graphics.TOP | Graphics.LEFT);
        return f.getHeight() + 8;
    }
}
