package j2pc.gui;

import j2pc.Options;
import j2pc.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

/**
 * Desktop shell for the web interface.
 *
 * <p>The interface is HTML served over loopback, which is what makes it easy to
 * iterate on and what lets the same front end run in a browser, in CI, or from a
 * script. Displaying a web page inside a Java window without adding a dependency
 * is not possible on a plain JDK — {@code JEditorPane} has no JavaScript engine and
 * JavaFX is not part of the JDK any more — so the shell opens the system browser and
 * keeps a small control window alive while the server runs.</p>
 *
 * <p>If a JavaFX runtime is later added to the distribution, only this class needs
 * to change: the server and the page stay exactly the same.</p>
 */
public final class Gui {

    private Gui() {
    }

    /** Starts the server and shows the UI. Blocks until the window is closed. */
    public static void start(Options opts) {
        WebServer server;
        try {
            server = new WebServer(opts, 0);
            server.start();
        } catch (IOException e) {
            Log.error("cannot start the local server: %s", e.getMessage());
            return;
        }

        String url = "http://127.0.0.1:" + server.port() + "/";
        Log.info("J2PC web UI: %s", url);

        // The window lives on the event dispatch thread, which does not keep the
        // JVM alive on its own: without something to wait for, the tool would
        // print the URL and exit before the page finished loading.
        CountDownLatch closed = new CountDownLatch(1);

        if (java.awt.GraphicsEnvironment.isHeadless()) {
            // No display: still a usable interface, just in a browser only.
            Log.info("no display available; open the URL above (Ctrl+C stops the server)");
            openBrowser(url);
            awaitForever(closed, server);
            return;
        }
        showWindow(url, opts.version, closed);
        awaitForever(closed, server);
    }

    private static void awaitForever(CountDownLatch closed, WebServer server) {
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop();
        }
    }

    /** Control window: the URL, an "open" button and a "stop" button. */
    private static void showWindow(String url, String version, CountDownLatch closed) {
        Runnable task = () -> {
            JFrame frame = new JFrame("J2PC " + version);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            JPanel top = new JPanel(new BorderLayout(8, 8));
            top.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));

            JLabel title = new JLabel("J2PC \u2014 J2ME static recompiler");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
            top.add(title, BorderLayout.NORTH);

            JLabel hint = new JLabel("<html>The interface runs in your browser. "
                    + "This window keeps the local server alive.</html>");
            hint.setForeground(new Color(0x55, 0x5F, 0x70));
            hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
            top.add(hint, BorderLayout.CENTER);

            JPanel row = new JPanel(new BorderLayout(6, 0));
            JTextField urlField = new JTextField(url);
            urlField.setEditable(false);
            urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            row.add(urlField, BorderLayout.CENTER);

            JButton open = new JButton("Open");
            open.addActionListener(e -> openBrowser(url));
            row.add(open, BorderLayout.EAST);
            top.add(row, BorderLayout.SOUTH);

            frame.add(top, BorderLayout.NORTH);

            JPanel actions = new JPanel(new GridLayout(1, 2, 8, 0));
            actions.setBorder(BorderFactory.createEmptyBorder(4, 16, 14, 16));
            JButton again = new JButton("Open again");
            again.addActionListener(e -> openBrowser(url));
            JButton stop = new JButton("Stop server");
            stop.addActionListener(e -> {
                frame.dispose();
            });
            actions.add(again);
            actions.add(stop);
            frame.add(actions, BorderLayout.SOUTH);

            frame.add(Box.createVerticalStrut(0), BorderLayout.CENTER);
            frame.setMinimumSize(new Dimension(520, 200));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    Log.info("server stopped");
                    closed.countDown();
                }
            });

            openBrowser(url);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | RuntimeException e) {
            Log.debug("cannot open a browser: %s", e.getMessage());
        }
        Log.info("open this URL manually: %s", url);
    }

    /** Entry point used by {@code --gui}. */
    public static void main(String[] args) {
        start(new Options());
    }
}
