package com.nokia.mid.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Nokia's {@code Sound} class.
 *
 * <p>Handles the two formats the API exposes: a single tone, and a byte array of
 * encoded audio. Tones are synthesised; encoded data is decoded with
 * {@code javax.sound} when the platform can, and silently ignored otherwise,
 * which matches the behaviour of a handset without that codec.</p>
 */
public class Sound {

    public static final int FORMAT_TONE = 1;
    public static final int FORMAT_WAV = 5;

    public static final int SOUND_PLAYING = 0;
    public static final int SOUND_STOPPED = 1;
    public static final int SOUND_UNINITIALIZED = 3;

    private static final int TONE_SEQUENCE_HEADER = 2;

    private final int format;
    private byte[] data;
    private long duration;
    private int state = SOUND_STOPPED;
    private int gain = 100;
    private int loopCount = 1;
    private SoundListener listener;
    private Thread player;

    public Sound(byte[] data, long duration) {
        this.format = FORMAT_WAV;
        this.data = data;
        this.duration = duration;
    }

    public Sound(int freq, long duration) {
        this.format = FORMAT_TONE;
        this.data = new byte[]{(byte) (freq & 0xFF), (byte) ((freq >> 8) & 0xFF)};
        this.duration = duration;
    }

    public static int getConcurrentSoundCount(int type) {
        return type == FORMAT_TONE ? 1 : 1;
    }

    public static int[] getSupportedFormats() {
        return new int[]{FORMAT_TONE, FORMAT_WAV};
    }

    public int getFormat() {
        return format;
    }

    public int getState() {
        return state;
    }

    public int getGain() {
        return gain;
    }

    public void setGain(int gain) {
        this.gain = Math.max(0, Math.min(100, gain));
    }

    public void setListener(SoundListener listener) {
        this.listener = listener;
    }

    public void init(byte[] data, long duration) {
        this.data = data;
        this.duration = duration;
        state = SOUND_STOPPED;
    }

    public void init(int freq, long duration) {
        this.data = new byte[]{(byte) (freq & 0xFF), (byte) ((freq >> 8) & 0xFF)};
        this.duration = duration;
        state = SOUND_STOPPED;
    }

    public void play(int loop) {
        this.loopCount = Math.max(1, loop);
        state = SOUND_PLAYING;
        if (player != null && player.isAlive()) {
            return;
        }
        player = new Thread(this::run, "nokia-sound");
        player.setDaemon(true);
        player.start();
        if (listener != null) {
            listener.soundStateChanged(this, SOUND_PLAYING);
        }
    }

    public void stop() {
        state = SOUND_STOPPED;
        if (player != null) {
            player.interrupt();
            player = null;
        }
        if (listener != null) {
            listener.soundStateChanged(this, SOUND_STOPPED);
        }
    }

    public void release() {
        stop();
        data = null;
        state = SOUND_UNINITIALIZED;
    }

    public void resume() {
        play(loopCount);
    }

    private void run() {
        try {
            if (format == FORMAT_TONE) {
                playTone();
            } else {
                playEncoded();
            }
        } catch (Exception e) {
            // audio is optional; a missing mixer must not break the game
        } finally {
            if (state == SOUND_PLAYING) {
                state = SOUND_STOPPED;
                if (listener != null) {
                    listener.soundStateChanged(this, SOUND_STOPPED);
                }
            }
        }
    }

    private void playTone() {
        int freq = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        if (freq <= 0) {
            return;
        }
        double seconds = duration / 1000.0;
        int sampleRate = 44100;
        int frames = Math.max(1, (int) (sampleRate * seconds));
        byte[] pcm = new byte[frames * 2];
        double amp = 32767.0 * (gain / 100.0);
        for (int i = 0; i < frames; i++) {
            double envelope = Math.min(1.0, Math.min(i, frames - i) / (sampleRate * 0.005));
            short v = (short) (Math.sin(2 * Math.PI * freq * i / sampleRate) * amp * envelope);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
        } catch (Exception e) {
            // no mixer: stay silent
        } finally {
            if (line != null) {
                line.close();
            }
        }
    }

    private void playEncoded() {
        if (data == null || data.length < 4) {
            return;
        }
        try (javax.sound.sampled.AudioInputStream in = AudioSystem.getAudioInputStream(
                new java.io.ByteArrayInputStream(data))) {
            AudioFormat fmt = in.getFormat();
            SourceDataLine line = null;
            try {
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    line.write(buffer, 0, n);
                }
                line.drain();
            } finally {
                if (line != null) {
                    line.close();
                }
            }
        } catch (Exception e) {
            // unsupported codec: stay silent
        }
    }
}
