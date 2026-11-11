package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import java.awt.image.BufferedImage;

/**
 * MIDP {@code GameCanvas}: a canvas with an off-screen buffer and polled key
 * state, the two features most J2ME games rely on.
 */
public abstract class GameCanvas extends Canvas {

    public static final int UP_PRESSED = 0x0002;
    public static final int DOWN_PRESSED = 0x0040;
    public static final int LEFT_PRESSED = 0x0004;
    public static final int RIGHT_PRESSED = 0x0020;
    public static final int FIRE_PRESSED = 0x0100;
    public static final int GAME_A_PRESSED = 0x0200;
    public static final int GAME_B_PRESSED = 0x0400;
    public static final int GAME_C_PRESSED = 0x0800;
    public static final int GAME_D_PRESSED = 0x1000;

    private final BufferedImage buffer;
    private final Graphics graphics;
    private final boolean suppressKeyEvents;
    private int keyStates;

    protected GameCanvas(boolean suppressKeyEvents) {
        this.suppressKeyEvents = suppressKeyEvents;
        this.buffer = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        this.graphics = Graphics.forCanvas(buffer.createGraphics(), buffer, 240, 320);
    }

    public Graphics getGraphics() {
        return graphics;
    }

    public void flushGraphics() {
        javax.microedition.lcdui.Display.flushGameCanvas(this, buffer);
    }

    public void flushGraphics(int x, int y, int width, int height) {
        flushGraphics();
    }

    public int getKeyStates() {
        int states = keyStates;
        keyStates = 0;
        return states;
    }

    @Override
    protected void keyPressed(int keyCode) {
        keyStates |= mask(keyCode);
    }

    @Override
    protected void keyReleased(int keyCode) {
        keyStates &= ~mask(keyCode);
    }

    private static int mask(int keyCode) {
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_UP: return UP_PRESSED;
            case java.awt.event.KeyEvent.VK_DOWN: return DOWN_PRESSED;
            case java.awt.event.KeyEvent.VK_LEFT: return LEFT_PRESSED;
            case java.awt.event.KeyEvent.VK_RIGHT: return RIGHT_PRESSED;
            case java.awt.event.KeyEvent.VK_ENTER:
            case java.awt.event.KeyEvent.VK_SPACE: return FIRE_PRESSED;
            case java.awt.event.KeyEvent.VK_Q: return GAME_A_PRESSED;
            case java.awt.event.KeyEvent.VK_E: return GAME_B_PRESSED;
            case java.awt.event.KeyEvent.VK_R: return GAME_C_PRESSED;
            case java.awt.event.KeyEvent.VK_T: return GAME_D_PRESSED;
            default: return 0;
        }
    }

    /** GameCanvas draws its own off-screen buffer; subclasses rarely override this. */
    @Override
    protected void paint(Graphics g) {
        g.drawImage(Image.wrap(buffer), 0, 0, Graphics.TOP | Graphics.LEFT);
    }

    boolean suppressesKeyEvents() {
        return suppressKeyEvents;
    }
}
