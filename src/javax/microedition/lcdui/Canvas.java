package javax.microedition.lcdui;

/**
 * MIDP {@code Canvas}: an application drawn surface with key handling.
 *
 * <p>Rendering is driven by {@link Display}, which calls
 * {@link #paint(Graphics)} whenever the surface is dirty.</p>
 */
public abstract class Canvas extends Displayable {

    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;

    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    private boolean fullScreen;

    protected Canvas() {
    }

    protected abstract void paint(Graphics g);

    /**
     * Key handling.
     *
     * <p>MIDP declares these {@code protected}, but recompiled code frequently calls
     * them on another instance — bytecode can, because it does not enforce the
     * rule the same way. Widening to {@code public} keeps the behaviour and lets the
     * generated source compile.</p>
     */
    public void keyPressed(int keyCode) {
    }

    public void keyReleased(int keyCode) {
    }

    public void keyRepeated(int keyCode) {
    }

    public void pointerPressed(int x, int y) {
    }

    public void pointerReleased(int x, int y) {
    }

    public void pointerDragged(int x, int y) {
    }

    public void sizeChanged(int w, int h) {
    }

    public final void repaint() {
        Display.requestRepaint(this);
    }

    public final void repaint(int x, int y, int width, int height) {
        Display.requestRepaint(this);
    }

    public final void serviceRepaints() {
        Display.serviceRepaints(this);
    }

    public final void setFullScreenMode(boolean mode) {
        this.fullScreen = mode;
    }

    public final boolean isFullScreenMode() {
        return fullScreen;
    }

    public boolean hasPointerEvents() {
        return true;
    }

    public boolean hasPointerMotionEvents() {
        return true;
    }

    public boolean hasRepeatEvents() {
        return true;
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP: return java.awt.event.KeyEvent.VK_UP;
            case DOWN: return java.awt.event.KeyEvent.VK_DOWN;
            case LEFT: return java.awt.event.KeyEvent.VK_LEFT;
            case RIGHT: return java.awt.event.KeyEvent.VK_RIGHT;
            case FIRE: return java.awt.event.KeyEvent.VK_ENTER;
            default: return 0;
        }
    }

    public int getGameAction(int keyCode) {
        // Numpad arrows are separate AWT codes but mean the same thing; the host
        // translates them before they arrive, so only the canonical codes appear.
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_UP:
            case java.awt.event.KeyEvent.VK_KP_UP:
            case java.awt.event.KeyEvent.VK_W: return UP;
            case java.awt.event.KeyEvent.VK_DOWN:
            case java.awt.event.KeyEvent.VK_KP_DOWN:
            case java.awt.event.KeyEvent.VK_S: return DOWN;
            case java.awt.event.KeyEvent.VK_LEFT:
            case java.awt.event.KeyEvent.VK_KP_LEFT:
            case java.awt.event.KeyEvent.VK_A: return LEFT;
            case java.awt.event.KeyEvent.VK_RIGHT:
            case java.awt.event.KeyEvent.VK_KP_RIGHT:
            case java.awt.event.KeyEvent.VK_D: return RIGHT;
            case java.awt.event.KeyEvent.VK_ENTER:
            case java.awt.event.KeyEvent.VK_SPACE: return FIRE;
            case java.awt.event.KeyEvent.VK_F1:
            case java.awt.event.KeyEvent.VK_Q: return GAME_A;
            case java.awt.event.KeyEvent.VK_F2:
            case java.awt.event.KeyEvent.VK_E: return GAME_B;
            case java.awt.event.KeyEvent.VK_F3:
            case java.awt.event.KeyEvent.VK_R: return GAME_C;
            case java.awt.event.KeyEvent.VK_F4:
            case java.awt.event.KeyEvent.VK_T: return GAME_D;
            default: return 0;
        }
    }

    public String getKeyName(int keyCode) {
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_UP: return "UP";
            case java.awt.event.KeyEvent.VK_DOWN: return "DOWN";
            case java.awt.event.KeyEvent.VK_LEFT: return "LEFT";
            case java.awt.event.KeyEvent.VK_RIGHT: return "RIGHT";
            case java.awt.event.KeyEvent.VK_ENTER: return "ENTER";
            case KEY_STAR: return "STAR";
            case KEY_POUND: return "POUND";
            default:
                if (keyCode >= KEY_NUM0 && keyCode <= KEY_NUM9) {
                    return String.valueOf((char) keyCode);
                }
                return java.awt.event.KeyEvent.getKeyText(keyCode);
        }
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    @Override
    protected void paintInternal(Graphics g) {
        paint(g);
    }

    @Override
    boolean keyEvent(int keyCode, boolean pressed) {
        int code = translateKeyCode(keyCode);
        if (pressed) {
            keyPressed(code);
        } else {
            keyReleased(code);
        }
        return true;
    }

    /**
     * Maps an AWT key code to the code MIDP would deliver.
     *
     * <p>A MIDlet compares {@code keyCode} against {@link #KEY_NUM5} and friends,
     * so the host cannot simply forward the AWT value: the numeric keypad reports
     * 96..105, the asterisk reports 106 and the number sign has no single AWT code.
     * Digits already agree — {@code KEY_NUM0} is {@code '0'} — which is why the
     * translation only touches the keys that do not.</p>
     */
    static int translateKeyCode(int awtKeyCode) {
        if (awtKeyCode >= java.awt.event.KeyEvent.VK_NUMPAD0
                && awtKeyCode <= java.awt.event.KeyEvent.VK_NUMPAD9) {
            return KEY_NUM0 + (awtKeyCode - java.awt.event.KeyEvent.VK_NUMPAD0);
        }
        switch (awtKeyCode) {
            case java.awt.event.KeyEvent.VK_MULTIPLY:
            case java.awt.event.KeyEvent.VK_ASTERISK:
                return KEY_STAR;
            case java.awt.event.KeyEvent.VK_NUMBER_SIGN:
                return KEY_POUND;
            case java.awt.event.KeyEvent.VK_KP_UP:
                return java.awt.event.KeyEvent.VK_UP;
            case java.awt.event.KeyEvent.VK_KP_DOWN:
                return java.awt.event.KeyEvent.VK_DOWN;
            case java.awt.event.KeyEvent.VK_KP_LEFT:
                return java.awt.event.KeyEvent.VK_LEFT;
            case java.awt.event.KeyEvent.VK_KP_RIGHT:
                return java.awt.event.KeyEvent.VK_RIGHT;
            default:
                return awtKeyCode;
        }
    }

    /** Internal hook used by the desktop host for pointer input. */
    @Override
    boolean pointerEvent(int x, int y, int kind) {
        if (kind == 0) {
            pointerPressed(x, y);
        } else if (kind == 1) {
            pointerReleased(x, y);
        } else if (hasPointerMotionEvents()) {
            pointerDragged(x, y);
        }
        return true;
    }

    void sizeChangedFromHost(int w, int h) {
        sizeChanged(w, h);
    }
}
