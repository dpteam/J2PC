package javax.microedition.media.control;

import javax.microedition.media.Control;

/** Playback rate control. */
public interface RateControl extends Control {

    int setRate(int milliRate);

    int getRate();

    int getMaxRate();

    int getMinRate();
}
