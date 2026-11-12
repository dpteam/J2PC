package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * MIDP {@code Display}: owns the single visible {@link Displayable} and the
 * desktop window that renders it.
 */
public final class Display {

    private static JFrame frame;
    private static Display instance;
    private static final ConcurrentLinkedQueue<Runnable> SERIAL = new ConcurrentLinkedQueue<>();

    private final MIDlet midlet;
    private Displayable current;
    private CanvasPanel panel;

    private Display(MIDlet midlet) {
        this.midlet = midlet;
    }

    /** Called by the desktop host before the first {@code setCurrent}. */
    public static void init(JFrame host) {
        frame = host;
    }
    public static Display getDisplay(MIDlet m) {
        if (m == null) {
            throw new NullPointerException("midlet");
        }
        if (instance == null) {
            instance = new Display(m);
        }
        return instance;
    }

    public void setCurrent(Displayable next) {
        if (next == null) {
            throw new NullPointerException("next");
        }
        Runnable task = () -> {
            if (current != null) {
                current.setShownInternal(false);
            }
            current = next;
            install(next);
            next.setShownInternal(true);
            requestRepaint(next);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /** MIDP 2.0 overload: show an alert, then continue with {@code next}. */
    public void setCurrent(Alert alert, Displayable next) {
        if (alert == null) {
            throw new NullPointerException("alert");
        }
        setCurrent(alert);
    }

    // ------------------------------------------- vendor display extensions

    /**
     * Suggested width for an image of the given width.
     *
     * <p>Not part of MIDP, but Nokia's S60 {@code Display} exposes these and games
     * call them to size their buffers. The emulation returns the screen size,
     * which is what the handset reported.</p>
     */
    public int getBestImageWidth(int imageType) {
        return 240;
    }

    public int getBestImageHeight(int imageType) {
        return 320;
    }

    /** Border style of the display, as the vendor API reports it. */
    public int getBorderStyle(boolean highlighted) {
        return 0;
    }

    /** Resolves a colour index to a displayable colour. */
    public int getColor(int colorIndex) {
        return colorIndex & 0xFFFFFF;
    }

    public Displayable getCurrent() {
        return current;
    }

    public void callSerially(Runnable r) {
        SERIAL.add(r);
        SwingUtilities.invokeLater(() -> {
            Runnable next;
            while ((next = SERIAL.poll()) != null) {
                next.run();
            }
        });
    }

    public boolean vibrate(int duration) {
        return false;
    }

    public boolean flashBacklight(int duration) {
        return false;
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 16777216;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public void setCurrentItem(Item item) {
        if (current instanceof Form) {
            ((Form) current).focus(item);
        }
    }

    // -------------------------------------------------------------- internals

    private void install(Displayable d) {
        if (frame == null) {
            return;
        }
        if (panel == null) {
            panel = new CanvasPanel();
            frame.add(panel, BorderLayout.CENTER);
            frame.pack();
        }
        panel.bind(d);
        frame.setTitle((d.getTitle().isEmpty() ? "MIDlet" : d.getTitle()) + "  [J2PC]");
    }

    static void requestRepaint(Displayable d) {
        if (instance != null && instance.panel != null) {
            instance.panel.repaint();
        }
    }

    static void serviceRepaints(Displayable d) {
        if (instance != null && instance.panel != null) {
            instance.panel.paintNow();
        }
    }

    /** Publishes a GameCanvas off-screen buffer to the window. */
    public static void flushGameCanvas(javax.microedition.lcdui.game.GameCanvas canvas,
                                       BufferedImage buffer) {
        if (instance != null && instance.panel != null) {
            instance.panel.paintNow();
        }
    }

    /** Swing component that renders a {@link Displayable}. */
    private static final class CanvasPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final BufferedImage buffer;
        private Displayable target;

        /** Scale and offset of the last paint, used to map pointer coordinates. */
        private double scaleX = 1;
        private double scaleY = 1;
        private int offsetX;
        private int offsetY;

        /** True while a button is held, so a drag outside the screen still tracks. */
        private boolean dragging;

        CanvasPanel() {
            setPreferredSize(new Dimension(240, 320));
            setBackground(java.awt.Color.BLACK);
            setFocusable(true);
            buffer = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (target != null) {
                        target.keyEvent(e.getKeyCode(), true);
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    if (target != null) {
                        target.keyEvent(e.getKeyCode(), false);
                    }
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    dragging = true;
                    if (target != null) {
                        target.pointerEvent(bufferX(e), bufferY(e), 0);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                    if (target != null) {
                        target.pointerEvent(bufferX(e), bufferY(e), 1);
                    }
                }
            });

            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging && target != null) {
                        target.pointerEvent(bufferX(e), bufferY(e), 2);
                    }
                }
            });
        }

        /**
         * Pointer x in the application's own coordinate system.
         *
         * <p>The window can be any size, while the MIDlet thinks in a fixed
         * 240x320 surface, so a raw mouse coordinate would be wrong for every tap
         * as soon as the window is resized. The inverse of the paint transform maps
         * it back; coordinates outside the surface are clamped to the edge, which is
         * what a real touchscreen reports when a finger leaves the panel.</p>
         */
        private int bufferX(MouseEvent e) {
            int x = (int) Math.round((e.getX() - offsetX) / scaleX);
            return Math.max(0, Math.min(buffer.getWidth() - 1, x));
        }

        private int bufferY(MouseEvent e) {
            int y = (int) Math.round((e.getY() - offsetY) / scaleY);
            return Math.max(0, Math.min(buffer.getHeight() - 1, y));
        }

        void bind(Displayable d) {
            this.target = d;
            requestFocusInWindow();
        }

        void paintNow() {
            java.awt.Graphics2D g = buffer.createGraphics();
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());
            if (target != null) {
                Graphics mg = Graphics.forCanvas(g, buffer, buffer.getWidth(), buffer.getHeight());
                target.paintInternal(mg);
            }
            g.dispose();
            paintImmediately(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            java.awt.Graphics2D g2 = buffer.createGraphics();
            g2.setColor(java.awt.Color.BLACK);
            g2.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());
            if (target != null) {
                Graphics mg = Graphics.forCanvas(g2, buffer, buffer.getWidth(), buffer.getHeight());
                target.paintInternal(mg);
            }
            g2.dispose();

            // Keep the aspect ratio: stretching the surface would distort the game
            // and, worse, make every touch land a few pixels off. The letterbox is
            // painted black, so it looks like a handset frame rather than a bug.
            double sx = getWidth() / (double) buffer.getWidth();
            double sy = getHeight() / (double) buffer.getHeight();
            double scale = Math.min(sx, sy);
            int w = (int) Math.round(buffer.getWidth() * scale);
            int h = (int) Math.round(buffer.getHeight() * scale);
            int ox = (getWidth() - w) / 2;
            int oy = (getHeight() - h) / 2;

            scaleX = w / (double) buffer.getWidth();
            scaleY = h / (double) buffer.getHeight();
            offsetX = ox;
            offsetY = oy;

            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.drawImage(buffer, ox, oy, w, h, null);
        }
    }
}
