package javax.microedition.lcdui;

/** Screen with a list of choices. */
public class List extends Screen implements Choice {

    public static final Command SELECT_COMMAND = new Command("Select", Command.SCREEN, 0);

    private final ChoiceGroup group;
    private int highlighted;

    public List(String title, int listType) {
        setTitleInternal(title);
        this.group = new ChoiceGroup(null, listType);
    }

    public List(String title, int listType, String[] stringElements, Image[] imageElements) {
        setTitleInternal(title);
        this.group = new ChoiceGroup(null, listType, stringElements, imageElements);
    }

    public int getType() {
        return group.getChoiceType();
    }

    @Override
    public int size() {
        return group.size();
    }

    @Override
    public String getString(int elementNum) {
        return group.getString(elementNum);
    }

    @Override
    public Image getImage(int elementNum) {
        return group.getImage(elementNum);
    }

    @Override
    public int getSelectedIndex() {
        return group.getSelectedIndex();
    }

    @Override
    public int getSelectedFlags(boolean[] out) {
        return group.getSelectedFlags(out);
    }

    @Override
    public void setSelectedIndex(int elementNum, boolean selected) {
        group.setSelectedIndex(elementNum, selected);
    }

    @Override
    public void setSelectedFlags(boolean[] flags) {
        group.setSelectedFlags(flags);
    }

    @Override
    public void append(String part, Image imagePart) {
        group.append(part, imagePart);
    }

    @Override
    public void insert(int elementNum, String part, Image imagePart) {
        group.insert(elementNum, part, imagePart);
    }

    @Override
    public void delete(int elementNum) {
        group.delete(elementNum);
    }

    @Override
    public void deleteAll() {
        group.deleteAll();
    }

    @Override
    public void set(int elementNum, String part, Image imagePart) {
        group.set(elementNum, part, imagePart);
    }

    @Override
    public boolean isSelected(int elementNum) {
        return group.isSelected(elementNum);
    }

    @Override
    public int getFitPolicy() {
        return group.getFitPolicy();
    }

    @Override
    public void setFitPolicy(int fitPolicy) {
        group.setFitPolicy(fitPolicy);
    }

    @Override
    public Font getFont(int elementNum) {
        return group.getFont(elementNum);
    }

    @Override
    public void setFont(int elementNum, Font font) {
        group.setFont(elementNum, font);
    }

    public void setSelectCommand(Command cmd) {
    }

    @Override
    void paintContent(Graphics g, int top) {
        group.paint(g, 0, top, g.getClipWidth());
    }
}
