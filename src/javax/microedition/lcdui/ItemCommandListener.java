package javax.microedition.lcdui;

/** Receives commands attached to the items of a {@link Form}. */
public interface ItemCommandListener {

    void commandAction(Command c, Item item);
}
