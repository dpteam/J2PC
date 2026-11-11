package javax.microedition.media.control;

/** Frame position value object. */
public final class FramePosition {

    private final long value;

    public FramePosition(long value) {
        this.value = value;
    }

    public long getFrameNumber() {
        return value;
    }
}
