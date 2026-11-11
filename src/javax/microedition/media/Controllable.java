package javax.microedition.media;

/** Provides access to the controls of a {@link Player}. */
public interface Controllable {

    Control getControl(String controlType);

    Control[] getControls();
}
