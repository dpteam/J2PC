package javax.microedition.lcdui;

/** The predefined alert types. */
public class AlertType {

    public static final AlertType INFO = new AlertType();
    public static final AlertType WARNING = new AlertType();
    public static final AlertType ERROR = new AlertType();
    public static final AlertType ALARM = new AlertType();
    public static final AlertType CONFIRMATION = new AlertType();

    protected AlertType() {
    }

    /** Plays the sound associated with this type; always succeeds here. */
    public boolean playSound(Display display) {
        return true;
    }
}
