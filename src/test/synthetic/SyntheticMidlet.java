package synthetic;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

/**
 * A complete MIDlet that touches the parts of the platform the emulation layer
 * implements: the lifecycle, a canvas with painting and key handling, commands,
 * a record store and image creation.
 *
 * <p>Used by the CI self-test so that the generated launcher can be compiled and
 * exercised without shipping a third-party application.</p>
 */
public final class SyntheticMidlet extends MIDlet implements CommandListener {

    private Display display;
    private SyntheticCanvas canvas;
    private Command exit;
    private Command reset;

    @Override
    protected void startApp() {
        display = Display.getDisplay(this);
        exit = new Command("Exit", Command.EXIT, 1);
        reset = new Command("Reset", Command.SCREEN, 1);
        canvas = new SyntheticCanvas();
        canvas.addCommand(exit);
        canvas.addCommand(reset);
        canvas.setCommandListener(this);
        canvas.setTitle("Synthetic");
        display.setCurrent(canvas);
    }

    @Override
    protected void pauseApp() {
        if (canvas != null) {
            canvas.pause();
        }
    }

    @Override
    protected void destroyApp(boolean unconditional) {
        if (canvas != null) {
            canvas.save();
        }
    }

    @Override
    public void commandAction(Command c, Displayable d) {
        if (c == exit) {
            notifyDestroyed();
        } else if (c == reset) {
            canvas.reset();
        }
    }

    /** A canvas that paints a grid and reacts to the arrow keys. */
    static final class SyntheticCanvas extends Canvas {

        private int cursorX;
        private int cursorY;
        private int moves;
        private Image buffer;

        SyntheticCanvas() {
            buffer = Image.createImage(16, 16);
        }

        void pause() {
            save();
        }

        void reset() {
            cursorX = 0;
            cursorY = 0;
            moves = 0;
            repaint();
        }

        @Override
        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();

            g.setColor(0xFFFFFF);
            g.fillRect(0, 0, w, h);

            g.setColor(0xCCCCCC);
            for (int x = 0; x < w; x += 16) {
                g.drawLine(x, 0, x, h);
            }
            for (int y = 0; y < h; y += 16) {
                g.drawLine(0, y, w, y);
            }

            g.setColor(0x3366FF);
            g.fillRect(cursorX * 16, cursorY * 16, 16, 16);

            g.setColor(0x000000);
            g.drawString("moves " + moves, w / 2, 4, Graphics.HCENTER | Graphics.TOP);
            g.drawString(cursorX + "," + cursorY, w / 2, h - 4,
                    Graphics.HCENTER | Graphics.BOTTOM);
        }

        @Override
        public void keyPressed(int keyCode) {
            switch (getGameAction(keyCode)) {
                case UP:
                    cursorY = Math.max(0, cursorY - 1);
                    moves++;
                    break;
                case DOWN:
                    cursorY = Math.min(getHeight() / 16 - 1, cursorY + 1);
                    moves++;
                    break;
                case LEFT:
                    cursorX = Math.max(0, cursorX - 1);
                    moves++;
                    break;
                case RIGHT:
                    cursorX = Math.min(getWidth() / 16 - 1, cursorX + 1);
                    moves++;
                    break;
                default:
                    return;
            }
            repaint();
        }

        void save() {
            try {
                RecordStore store = RecordStore.openRecordStore("synthetic", true);
                byte[] data = new byte[]{(byte) cursorX, (byte) cursorY, (byte) moves};
                if (store.getNumRecords() == 0) {
                    store.addRecord(data, 0, data.length);
                } else {
                    store.setRecord(store.getNextRecordID() - 1, data, 0, data.length);
                }
                store.closeRecordStore();
            } catch (Exception e) {
                // saving is best effort
            }
        }
    }
}
