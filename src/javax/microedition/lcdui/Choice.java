package javax.microedition.lcdui;

/** Common interface of the choice widgets. */
public interface Choice {

    int EXCLUSIVE = 1;
    int MULTIPLE = 2;
    int IMPLICIT = 3;
    int POPUP = 4;
    int TEXT_WRAP_ON = 1;
    int TEXT_WRAP_OFF = 2;
    int TEXT_WRAP_DEFAULT = 0;

    int size();

    String getString(int elementNum);

    Image getImage(int elementNum);

    int getSelectedIndex();

    int getSelectedFlags(boolean[] selectedArray_return);

    void setSelectedIndex(int elementNum, boolean selected);

    void setSelectedFlags(boolean[] selectedArray);

    /**
     * Appends an element and returns its index.
     *
     * <p>MIDP returns the new element's index. Callers written against a
     * {@code void} signature simply ignore the result, so returning it keeps both
     * kinds of suite compiling — and a suite that implements this interface must
     * match the real signature or javac reports "cannot implement".</p>
     */
    int append(String stringPart, Image imagePart);

    void insert(int elementNum, String stringPart, Image imagePart);

    void delete(int elementNum);

    void deleteAll();

    void set(int elementNum, String stringPart, Image imagePart);

    boolean isSelected(int elementNum);

    int getFitPolicy();

    void setFitPolicy(int fitPolicy);

    Font getFont(int elementNum);

    void setFont(int elementNum, Font font);
}
