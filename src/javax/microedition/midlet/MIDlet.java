package javax.microedition.midlet;

/**
 * Minimal MIDlet base class for desktop execution.
 *
 * <p>The lifecycle is driven by {@link MIDletHost}; applications only need to
 * implement {@link #startApp()}, {@link #pauseApp()} and
 * {@link #destroyApp(boolean)}.</p>
 */
public abstract class MIDlet {

    private boolean destroyed;
    private boolean paused;

    protected MIDlet() {
    }

    protected abstract void startApp() throws MIDletStateChangeException;

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    /** Requests a transition to the paused state. */
    public final void notifyPaused() {
        paused = true;
    }

    /** Leaves the paused state and calls {@link #startApp()}. */
    public final void resumeRequest() {
        if (paused) {
            paused = false;
            try {
                startApp();
            } catch (MIDletStateChangeException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Asks the host to destroy the application. */
    public final void notifyDestroyed() {
        destroyed = true;
        MIDletHost.requestExit(this);
    }

    public final boolean isDestroyed() {
        return destroyed;
    }

    public final String getAppProperty(String key) {
        return MIDletHost.getProperty(key);
    }

    /**
     * Asks the platform to open a URL.
     *
     * <p>MIDP returns {@code true} when the platform handled the request. The
     * desktop host hands it to the operating system's default browser.</p>
     */
    public final boolean platformRequest(String url) throws javax.microedition.io.ConnectionNotFoundException {
        if (url == null) {
            throw new javax.microedition.io.ConnectionNotFoundException("null url");
        }
        try {
            java.awt.Desktop desktop = java.awt.Desktop.isDesktopSupported()
                    ? java.awt.Desktop.getDesktop() : null;
            if (desktop != null && desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI.create(url));
                return true;
            }
            return false;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new javax.microedition.io.ConnectionNotFoundException(e.getMessage());
        }
    }

    /** Asks the host for the size of a scratch buffer, as MIDP 2.0 allows. */
    public final int checkPermission(String permission) {
        return 1;
    }

    /** Called by the host on shutdown. */
    final void destroyFromHost() {
        try {
            destroyApp(true);
        } catch (MIDletStateChangeException e) {
            // the application refused to die; nothing sensible left to do
        }
        destroyed = true;
    }

    final void startFromHost() throws MIDletStateChangeException {
        paused = false;
        startApp();
    }
}
