package javax.microedition.media;

/** Factory for {@link Player} instances. */
public final class Manager {

    public static final String TONE_DEVICE_LOCATOR = "device://tone";

    private Manager() {
    }

    public static Player createPlayer(String locator) throws java.io.IOException, MediaException {
        return new TonePlayer(locator);
    }

    public static Player createPlayer(java.io.InputStream stream, String type)
            throws java.io.IOException, MediaException {
        return new TonePlayer("stream:" + type);
    }

    public static Player createPlayer(DataSource source) throws java.io.IOException, MediaException {
        return new TonePlayer(source == null ? "data" : source.getContentType());
    }

    public static void playTone(int note, int duration, int volume) throws MediaException {
        TonePlayer player = new TonePlayer(TONE_DEVICE_LOCATOR);
        player.setNote(note, duration, volume);
        player.start();
    }
}
