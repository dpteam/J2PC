package com.nokia.mid.ui;

import javax.microedition.lcdui.Image;

/** Factory for the Nokia extended graphics API. */
public final class DirectUtils {

    private DirectUtils() {
    }

    /** Returns the {@link DirectGraphics} view of a MIDP graphics context. */
    public static DirectGraphics getDirectGraphics(javax.microedition.lcdui.Graphics g) {
        if (g instanceof DirectGraphics) {
            return (DirectGraphics) g;
        }
        throw new IllegalArgumentException("graphics was not created by the emulation layer");
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        return Image.createImage(imageData, imageOffset, imageLength);
    }

    public static Image createImage(int width, int height, int argb) {
        Image img = Image.createImage(width, height);
        javax.microedition.lcdui.Graphics g = img.getGraphics();
        g.setColor(argb & 0xFFFFFF);
        g.fillRect(0, 0, width, height);
        return img;
    }
}
