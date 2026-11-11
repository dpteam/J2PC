package javax.microedition.midlet;

/** Thrown when a MIDlet cannot change to the requested state. */
public class MIDletStateChangeException extends Exception {

    private static final long serialVersionUID = 1L;

    public MIDletStateChangeException() {
        super();
    }

    public MIDletStateChangeException(String message) {
        super(message);
    }
}
