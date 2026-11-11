package javax.microedition.lcdui;

/** Base class of everything that can be shown on a MIDP display. */
public abstract class Displayable {

    private String title = "";
    private Ticker ticker;
    private Command[] commands = new Command[0];
    private CommandListener listener;
    private boolean shown;

    void setTitleInternal(String title) {
        this.title = title == null ? "" : title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public void setTicker(Ticker ticker) {
        this.ticker = ticker;
    }

    public void addCommand(Command cmd) {
        if (cmd == null) {
            throw new NullPointerException("command");
        }
        for (Command c : commands) {
            if (c == cmd) {
                return;
            }
        }
        Command[] next = new Command[commands.length + 1];
        System.arraycopy(commands, 0, next, 0, commands.length);
        next[commands.length] = cmd;
        commands = next;
    }

    public void removeCommand(Command cmd) {
        for (int i = 0; i < commands.length; i++) {
            if (commands[i] == cmd) {
                Command[] next = new Command[commands.length - 1];
                System.arraycopy(commands, 0, next, 0, i);
                System.arraycopy(commands, i + 1, next, i, commands.length - i - 1);
                commands = next;
                return;
            }
        }
    }

    public Command[] getCommands() {
        return commands.clone();
    }

    public void setCommandListener(CommandListener l) {
        this.listener = l;
    }

    public CommandListener getCommandListener() {
        return listener;
    }

    /** Sends a command event to the registered listener. */
    protected void fireCommand(Command cmd) {
        if (listener != null && cmd != null) {
            listener.commandAction(cmd, this);
        }
    }

    public boolean isShown() {
        return shown;
    }

    void setShownInternal(boolean shown) {
        this.shown = shown;
        if (shown) {
            showNotify();
        } else {
            hideNotify();
        }
    }

    protected void showNotify() {
    }

    protected void hideNotify() {
    }

    public int getWidth() {
        return 240;
    }

    public int getHeight() {
        return 320;
    }

    /** Paints this displayable into the given graphics context. */
    protected abstract void paintInternal(Graphics g);

    /** Delivers a key press; returns true when the event was consumed. */
    boolean keyEvent(int keyCode, boolean pressed) {
        return false;
    }
}
