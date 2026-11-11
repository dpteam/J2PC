package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;

/** Ordered collection of layers with a view window. */
public class LayerManager {

    private Layer[] layers = new Layer[0];
    private int viewX;
    private int viewY;
    private int viewW = 240;
    private int viewH = 320;

    public LayerManager() {
    }

    public void append(Layer layer) {
        Layer[] next = new Layer[layers.length + 1];
        System.arraycopy(layers, 0, next, 0, layers.length);
        next[layers.length] = layer;
        layers = next;
        layer.setLayerManager(this);
    }

    public void insert(Layer layer, int index) {
        append(layer);
    }

    public void remove(Layer layer) {
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] == layer) {
                Layer[] next = new Layer[layers.length - 1];
                System.arraycopy(layers, 0, next, 0, i);
                System.arraycopy(layers, i + 1, next, i, layers.length - i - 1);
                layers = next;
                return;
            }
        }
    }

    public Layer getLayerAt(int index) {
        return layers[index];
    }

    public int getSize() {
        return layers.length;
    }

    public void setViewWindow(int x, int y, int width, int height) {
        this.viewX = x;
        this.viewY = y;
        this.viewW = width;
        this.viewH = height;
    }

    public void paint(Graphics g, int x, int y) {
        int savedX = g.getTranslateX();
        int savedY = g.getTranslateY();
        g.translate(x - viewX, y - viewY);
        g.clipRect(viewX, viewY, viewW, viewH);
        for (Layer layer : layers) {
            layer.paint(g);
        }
        g.translate(savedX - g.getTranslateX(), savedY - g.getTranslateY());
    }
}
