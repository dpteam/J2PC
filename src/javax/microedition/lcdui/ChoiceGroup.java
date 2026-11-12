package javax.microedition.lcdui;

/** A list of selectable entries. */
public class ChoiceGroup extends Item implements Choice {

    private final int choiceType;
    private String[] strings = new String[0];
    private Image[] images = new Image[0];
    private boolean[] selected = new boolean[0];
    private int fitPolicy;

    public ChoiceGroup(String label, int choiceType) {
        super(label);
        this.choiceType = choiceType;
    }

    public ChoiceGroup(String label, int choiceType, String[] stringElements, Image[] imageElements) {
        super(label);
        this.choiceType = choiceType;
        for (int i = 0; i < stringElements.length; i++) {
            append(stringElements[i], imageElements == null ? null : imageElements[i]);
        }
    }

    public int getChoiceType() {
        return choiceType;
    }

    @Override
    public int size() {
        return strings.length;
    }

    @Override
    public String getString(int elementNum) {
        return strings[elementNum];
    }

    @Override
    public Image getImage(int elementNum) {
        return images[elementNum];
    }

    @Override
    public int getSelectedIndex() {
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getSelectedFlags(boolean[] out) {
        int count = 0;
        for (int i = 0; i < out.length; i++) {
            out[i] = i < selected.length && selected[i];
            if (out[i]) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void setSelectedIndex(int elementNum, boolean isSelected) {
        if (choiceType != MULTIPLE) {
            java.util.Arrays.fill(selected, false);
        }
        selected[elementNum] = isSelected;
    }

    @Override
    public void setSelectedFlags(boolean[] flags) {
        for (int i = 0; i < selected.length && i < flags.length; i++) {
            selected[i] = flags[i];
        }
    }

    @Override
    public int append(String part, Image imagePart) {
        String[] s = new String[strings.length + 1];
        Image[] im = new Image[images.length + 1];
        boolean[] sel = new boolean[selected.length + 1];
        System.arraycopy(strings, 0, s, 0, strings.length);
        System.arraycopy(images, 0, im, 0, images.length);
        System.arraycopy(selected, 0, sel, 0, selected.length);
        s[strings.length] = part;
        im[images.length] = imagePart;
        strings = s;
        images = im;
        selected = sel;
        return strings.length - 1;
    }

    @Override
    public void insert(int elementNum, String part, Image imagePart) {
        append(part, imagePart);
    }

    @Override
    public void delete(int elementNum) {
        String[] s = new String[strings.length - 1];
        for (int i = 0, j = 0; i < strings.length; i++) {
            if (i != elementNum) {
                s[j++] = strings[i];
            }
        }
        strings = s;
    }

    @Override
    public void deleteAll() {
        strings = new String[0];
        images = new Image[0];
        selected = new boolean[0];
    }

    @Override
    public void set(int elementNum, String part, Image imagePart) {
        strings[elementNum] = part;
        images[elementNum] = imagePart;
    }

    @Override
    public boolean isSelected(int elementNum) {
        return selected[elementNum];
    }

    @Override
    public int getFitPolicy() {
        return fitPolicy;
    }

    @Override
    public void setFitPolicy(int fitPolicy) {
        this.fitPolicy = fitPolicy;
    }

    @Override
    public Font getFont(int elementNum) {
        return Font.getDefaultFont();
    }

    @Override
    public void setFont(int elementNum, Font font) {
    }

    @Override
    int height(int width) {
        return Math.max(1, strings.length) * (Font.getDefaultFont().getHeight() + 2);
    }

    @Override
    int paint(Graphics g, int x, int y, int width) {
        Font f = Font.getDefaultFont();
        g.setFont(f);
        g.setColor(0x000000);
        int lineHeight = f.getHeight() + 2;
        for (int i = 0; i < strings.length; i++) {
            String prefix = selected[i] ? "[x] " : "[ ] ";
            g.drawString(prefix + strings[i], x + 4, y + i * lineHeight, Graphics.TOP | Graphics.LEFT);
        }
        return height(width);
    }
}
