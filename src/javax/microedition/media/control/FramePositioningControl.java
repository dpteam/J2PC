package javax.microedition.media.control;

import javax.microedition.media.Control;

/** Frame position control. */
public interface FramePositioningControl extends Control {

    long seek(int time);

    int skip(int framesToSkip);

    FramePosition mapFrameToTime(int frameNumber);

    int mapTimeToFrame(long mediaTime);
}
