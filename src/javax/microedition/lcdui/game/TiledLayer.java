package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Grid of image tiles. */
public class TiledLayer extends Layer {

    private final int cols;
    private final int rows;
    private final int tileWidth;
    private final int tileHeight;
    private final Image source;
    private final int[][] cells;
    private int animatedTileCount;

    public TiledLayer(int columns, int rows, Image image, int tileWidth, int tileHeight) {
        super(columns * tileWidth, rows * tileHeight);
        if (image.getWidth() % tileWidth != 0 || image.getHeight() % tileHeight != 0) {
            throw new IllegalArgumentException("image size is not a multiple of the tile size");
        }
        this.cols = columns;
        this.rows = rows;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.source = image;
        this.cells = new int[columns][rows];
    }

    public int createAnimatedTile(int staticTileIndex) {
        return -1 - (++animatedTileCount);
    }

    public void setAnimatedTile(int animatedTileIndex, int staticTileIndex) {
    }

    public int getAnimatedTile(int animatedTileIndex) {
        return 0;
    }

    public void setCell(int col, int row, int tileIndex) {
        cells[col][row] = tileIndex;
    }

    public int getCell(int col, int row) {
        return cells[col][row];
    }

    public void setStaticTileSet(Image image, int tileWidth, int tileHeight) {
    }

    public int getColumns() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public void fillCells(int col, int row, int numCols, int numRows, int tileIndex) {
        for (int c = col; c < col + numCols && c < cols; c++) {
            for (int r = row; r < row + numRows && r < rows; r++) {
                cells[c][r] = tileIndex;
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        if (!visible) {
            return;
        }
        int tilesPerRow = source.getWidth() / tileWidth;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int tile = cells[c][r];
                if (tile <= 0) {
                    continue;
                }
                int index = tile - 1;
                int sx = (index % tilesPerRow) * tileWidth;
                int sy = (index / tilesPerRow) * tileHeight;
                g.drawRegion(source, sx, sy, tileWidth, tileHeight, 0,
                        x + c * tileWidth, y + r * tileHeight, Graphics.TOP | Graphics.LEFT);
            }
        }
    }
}
