package javax.microedition.lcdui;

/** Base class of the items that can be placed in a {@link Form}. */
public abstract class Item {

    private String label = "";
    private int layout;

    protected Item(String label) {
        this.label = label == null ? "" : label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public int getLayout() {
        return layout;
    }

    public void setLayout(int layout) {
        this.layout = layout;
    }

    public int getMinimumWidth() {
        return 0;
    }

    public int getMinimumHeight() {
        return 0;
    }

    public int getPreferredWidth() {
        return 0;
    }

    public int getPreferredHeight() {
        return 0;
    }

    public void setPreferredSize(int width, int height) {
    }

    public void notifyStateChanged() {
    }

    /** Commands attached to this item, as MIDP 2.0 allows. */
    public void addCommand(Command cmd) {
        commands.add(cmd);
    }

    public void removeCommand(Command cmd) {
        commands.remove(cmd);
    }

    public void setItemCommandListener(ItemCommandListener l) {
        this.listener = l;
    }

    public void setDefaultCommand(Command cmd) {
        this.defaultCommand = cmd;
    }

    public Command getDefaultCommand() {
        return defaultCommand;
    }

    private final java.util.List<Command> commands = new java.util.ArrayList<>();
    private ItemCommandListener listener;
    private Command defaultCommand;

    abstract int paint(Graphics g, int x, int y, int width);

    abstract int height(int width);
}
