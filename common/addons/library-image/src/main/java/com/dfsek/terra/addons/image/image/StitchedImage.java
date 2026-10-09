package com.dfsek.terra.addons.image.image;

import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicInteger;

public class StitchedImage implements Image {

    private final Image[][] images;
    private final int[] rowOffsets, columnOffsets;
    private final int width, height;

    // -- Optimization Flags --
    private final boolean uniformWidth;
    private final boolean uniformHeight;
    private final int tileWidth;
    private final int tileHeight;

    public StitchedImage(Image[][] images, boolean zeroIndexed) throws IllegalArgumentException {
        long startTime = System.currentTimeMillis();

        int rows = images.length;
        if (rows == 0) {
            throw new IllegalArgumentException("Image array cannot be empty");
        }
        int columns = images[0].length;
        if (columns == 0) {
            throw new IllegalArgumentException("Image array cannot be empty");
        }

        System.out.println("[Terra] Starting Image Stitching (" + rows + " rows, " + columns + " cols)...");

        AtomicInteger processedTiles = new AtomicInteger(0);
        int totalTiles = rows * columns;

        // Validate row heights (and trigger loading in parallel)
        IntStream.range(0, totalTiles).parallel().forEach(index -> {
            int r = index / columns;
            int c = index % columns;
            int h = images[r][c].getHeight(); // Trigger load
            
            // Validate against first column in row
            if (c > 0 && h != images[r][0].getHeight()) {
                throw new IllegalArgumentException("Image heights in row " + (r + (zeroIndexed ? 0 : 1)) + " do not match");
            }

            int p = processedTiles.incrementAndGet();
            if (totalTiles > 100 && p % (totalTiles / 10) == 0) {
                 System.out.println("[Terra] Validating/Loading Images: " + (p * 100 / totalTiles) + "%");
            }
        });

        // Validate column widths
        IntStream.range(0, totalTiles).parallel().forEach(index -> {
            int r = index / columns;
            int c = index % columns;
            int w = images[r][c].getWidth();
            
            // Validate against first row in column
            if (r > 0 && w != images[0][c].getWidth()) {
                throw new IllegalArgumentException("Image widths in column " + (c + (zeroIndexed ? 0 : 1)) + " do not match");
            }
        });

        this.rowOffsets = new int[rows];
        this.columnOffsets = new int[columns];

        int tempHeight = 0;
        boolean allRowsSameHeight = true;
        int firstRowHeight = images[0][0].getHeight();

        for (int i = 0; i < rows; i++) {
            int rowHeight = images[i][0].getHeight();
            if (rowHeight != firstRowHeight) allRowsSameHeight = false;
            rowOffsets[i] = tempHeight;
            tempHeight += rowHeight;
        }

        int tempWidth = 0;
        boolean allColsSameWidth = true;
        int firstColWidth = images[0][0].getWidth();

        for (int i = 0; i < columns; i++) {
            int colWidth = images[0][i].getWidth();
            if (colWidth != firstColWidth) allColsSameWidth = false;
            columnOffsets[i] = tempWidth;
            tempWidth += colWidth;
        }

        this.width = tempWidth;
        this.height = tempHeight;
        this.images = images;

        this.uniformHeight = allRowsSameHeight;
        this.uniformWidth = allColsSameWidth;
        this.tileHeight = firstRowHeight;
        this.tileWidth = firstColWidth;

        long endTime = System.currentTimeMillis();
        System.out.println("[Terra] Image Stitching Complete! Took " + (endTime - startTime) + "ms.");
    }

    private int getColumn(int x) {
        if (uniformWidth) return x / tileWidth;
        for(int i = columnOffsets.length - 1; i > 0; i--) {
            if(x >= columnOffsets[i]) return i;
        }
        return 0;
    }

    private int getRow(int y) {
        if (uniformHeight) return y / tileHeight;
        for(int i = rowOffsets.length - 1; i > 0; i--) {
            if(y >= rowOffsets[i]) return i;
        }
        return 0;
    }

    @Override
    public int getRGB(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        int row = getRow(y);
        int column = getColumn(x);
        return images[row][column].getRGB(x - columnOffsets[column], y - rowOffsets[row]);
    }

    @Override
    public int getWidth() { return width; }

    @Override
    public int getHeight() { return height; }
}
