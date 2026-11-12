package com.nokia.mid.ui;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;

/**
 * Nokia's full-screen canvas.
 *
 * <p>Not part of MIDP, but extremely common in games: it removes the soft-key
 * area and adds a few key codes. The desktop emulation simply behaves like a
 * {@link Canvas} in full-screen mode.</p>
 */
public abstract class FullCanvas extends Canvas {

    public static final int KEY_SOFTKEY1 = -6;
    public static final int KEY_SOFTKEY2 = -7;
    public static final int KEY_SOFTKEY3 = -5;
    public static final int KEY_UP = -1;
    public static final int KEY_DOWN = -2;
    public static final int KEY_LEFT = -3;
    public static final int KEY_RIGHT = -4;
    public static final int KEY_SEND = -10;
    public static final int KEY_END = -11;

    protected FullCanvas() {
        setFullScreenMode(true);
    }

    @Override
    public void addCommand(javax.microedition.lcdui.Command cmd) {
        throw new IllegalStateException("FullCanvas does not support commands");
    }

    @Override
    public void setCommandListener(CommandListener l) {
        throw new IllegalStateException("FullCanvas does not support commands");
    }

    @Override
    public void setTitle(String s) {
        throw new IllegalStateException("FullCanvas has no title");
    }

    /** Deliberately not implemented: subclasses must paint. */
    @Override
    protected abstract void paint(Graphics g);
}
