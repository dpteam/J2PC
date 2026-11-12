package javax.microedition.media.control;

import javax.microedition.media.Control;

/** MIDI channel control. */
public interface MIDIControl extends Control {

    int NOTE_ON = 0x90;
    int NOTE_OFF = 0x80;
    int CONTROL_CHANGE = 0xB0;
    int PROGRAM_CHANGE = 0xC0;

    boolean isBankQuerySupported();

    int setProgram(int channel, int bank, int program);

    void setProgram(int channel, int program);

    int getProgram(int channel);

    int getChannelVolume(int channel);

    void setChannelVolume(int channel, int volume);

    int[] getBankList(boolean custom);

    int[] getProgramList(int bank);

    String getProgramName(int bank, int program);

    String getKeyName(int bank, int program, int key);

    void shortMidiEvent(int type, int data1, int data2);

    int longMidiEvent(byte[] data, int offset, int length);
}
