package com.nokia.mid.ui;

/**
 * Nokia's device control API: vibration, lights, keypad tones.
 *
 * <p>The desktop emulation accepts every request and does nothing. Games call
 * these for feedback; ignoring them is the correct behaviour on a machine
 * without a vibration motor.</p>
 */
public final class DeviceControl {

    private DeviceControl() {
    }

    public static void setLights(int num, int level) {
    }

    public static void flashLights(long duration) {
    }

    public static void startVibra(int frequency, long duration) {
    }

    public static void stopVibra() {
    }

    public static void setScreenState(int state) {
    }
}
