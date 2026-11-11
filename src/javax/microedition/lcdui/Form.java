package javax.microedition.lcdui;

/** A screen that holds a list of {@link Item}s. */
public class Form extends Screen {

    private Item[] items = new Item[0];
    private Item focused;

    public Form(String title) {
        setTitleInternal(title);
    }

    public Form(String title, Item[] items) {
        setTitleInternal(title);
        for (Item i : items) {
            append(i);
        }
    }

    public int size() {
        return items.length;
    }

    public Item get(int index) {
        return items[index];
    }

    public void append(Item item) {
        if (item == null) {
            throw new NullPointerException("item");
        }
        Item[] next = new Item[items.length + 1];
        System.arraycopy(items, 0, next, 0, items.length);
        next[items.length] = item;
        items = next;
    }

    public void append(String text) {
        append(new StringItem(null, text));
    }

    public void append(Image img) {
        append(new ImageItem(null, img, ImageItem.LAYOUT_DEFAULT, null));
    }

    public void insert(int index, Item item) {
        Item[] next = new Item[items.length + 1];
        System.arraycopy(items, 0, next, 0, index);
        next[index] = item;
        System.arraycopy(items, index, next, index + 1, items.length - index);
        items = next;
    }

    public void delete(int index) {
        Item[] next = new Item[items.length - 1];
        System.arraycopy(items, 0, next, 0, index);
        System.arraycopy(items, index + 1, next, index, items.length - index - 1);
        items = next;
    }

    public void deleteAll() {
        items = new Item[0];
    }

    public void set(int index, Item item) {
        items[index] = item;
    }

    public void setItemStateListener(ItemStateListener l) {
    }

    void focus(Item item) {
        this.focused = item;
    }

    @Override
    void paintContent(Graphics g, int top) {
        int y = top;
        int width = g.getClipWidth();
        for (Item item : items) {
            int h = item.height(width);
            if (item == focused) {
                g.setColor(0xCCCCFF);
                g.fillRect(0, y, width, h);
                g.setColor(0x000000);
            }
            y += item.paint(g, 0, y, width) + 2;
        }
    }

    @Override
    boolean keyEvent(int keyCode, boolean pressed) {
        return super.keyEvent(keyCode, pressed);
    }
}
