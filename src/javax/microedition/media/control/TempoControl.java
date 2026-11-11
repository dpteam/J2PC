package javax.microedition.media.control;

import javax.microedition.media.Control;

/** Tempo control. */
public interface TempoControl extends Control {

    int setTempo(int milliTempo);

    int getTempo();
}
