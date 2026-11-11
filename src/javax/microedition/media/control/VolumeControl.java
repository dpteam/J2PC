package javax.microedition.media.control;

import javax.microedition.media.Control;

/** Volume control of a player. */
public interface VolumeControl extends Control {

    int setLevel(int level);

    int getLevel();

    void setMute(boolean mute);

    boolean isMuted();
}
