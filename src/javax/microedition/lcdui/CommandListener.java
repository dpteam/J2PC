package javax.microedition.lcdui;

/** Receives {@link Command} invocations. */
public interface CommandListener {

    void commandAction(Command c, Displayable d);
}
