package javax.microedition.media;

/** Media failure. */
public class MediaException extends Exception {

    private static final long serialVersionUID = 1L;

    public MediaException() {
        super();
    }

    public MediaException(String message) {
        super(message);
    }
}
