package javax.microedition.lcdui;

/** Scrolling text shown at the bottom of the screen. */
public class Ticker {

    private String text;

    public Ticker(String text) {
        setString(text);
    }

    public String getString() {
        return text;
    }

    public void setString(String text) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        this.text = text;
    }
}
