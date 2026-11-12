package com.nokia.mid.sound;

/** Receives sound state notifications. */
public interface SoundListener {

    void soundStateChanged(Sound sound, int event);
}
