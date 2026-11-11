package javax.microedition.media;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Minimal {@link Player} that renders single tones.
 *
 * <p>J2ME games mostly use {@code Manager.playTone} for bleeps; full audio
 * decoding is out of scope for the first release, so other content types are
 * accepted but stay silent.</p>
 */
final class TonePlayer implements Player {

    private final String locator;
    private int state = UNREALIZED;
    private int loopCount = 1;
    private int note = 69;
    private int duration = 200;
    private int volume = 100;

    TonePlayer(String locator) {
        this.locator = locator;
    }

    void setNote(int note, int duration, int volume) {
        this.note = note;
        this.duration = duration;
        this.volume = volume;
    }

    @Override
    public void realize() {
        state = REALIZED;
    }

    @Override
    public void prefetch() {
        state = PREFETCHED;
    }

    @Override
    public void start() {
        state = STARTED;
        Thread t = new Thread(this::play, "j2pc-tone");
        t.setDaemon(true);
        t.start();
    }

    private void play() {
        try {
            double freq = 440.0 * Math.pow(2.0, (note - 69) / 12.0);
            int sampleRate = 44100;
            int frames = Math.max(1, (int) (sampleRate * (duration / 1000.0)));
            byte[] data = new byte[frames * 2];
            double amp = 32767.0 * (volume / 100.0);
            for (int i = 0; i < frames; i++) {
                double envelope = Math.min(1.0, Math.min(i, frames - i) / (sampleRate * 0.005));
                short v = (short) (Math.sin(2 * Math.PI * freq * i / sampleRate) * amp * envelope);
                data[i * 2] = (byte) (v & 0xFF);
                data[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
            }
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();
            line.write(data, 0, data.length);
            line.drain();
            line.close();
        } catch (Exception e) {
            // audio is optional; a missing mixer must not crash the game
        } finally {
            state = PREFETCHED;
        }
    }

    @Override
    public void stop() {
        state = PREFETCHED;
    }

    @Override
    public void deallocate() {
        state = REALIZED;
    }

    @Override
    public void close() {
        state = CLOSED;
    }

    @Override
    public void setLoopCount(int count) {
        this.loopCount = count;
    }

    @Override
    public long setMediaTime(long now) {
        return now;
    }

    @Override
    public long getMediaTime() {
        return 0;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public String getContentType() {
        return locator.startsWith("stream:") ? locator.substring(7) : "audio/x-tone-seq";
    }

    @Override
    public void addPlayerListener(PlayerListener listener) {
    }

    @Override
    public void removePlayerListener(PlayerListener listener) {
    }

    @Override
    public Control getControl(String controlType) {
        return null;
    }

    @Override
    public Control[] getControls() {
        return new Control[0];
    }
}
