package javax.microedition.media.control;

import javax.microedition.media.Control;

/** Video rendering surface. */
public interface VideoControl extends Control {

    int LAYOUT_DEFAULT = 0;
    int LAYOUT_LEFT = 1;
    int LAYOUT_RIGHT = 2;
    int LAYOUT_CENTER = 3;
    int LAYOUT_TOP = 0x10;
    int LAYOUT_BOTTOM = 0x20;
    int LAYOUT_VCENTER = 0x30;

    Object initDisplayMode(int mode, Object arg);

    void setDisplayLocation(int x, int y);

    void setDisplayFullScreen(boolean fullScreenMode);

    int getDisplayWidth();

    int getDisplayHeight();

    void setVisible(boolean visible);

    byte[] getSnapshot(String imageType);

    int getSourceWidth();

    int getSourceHeight();
}
