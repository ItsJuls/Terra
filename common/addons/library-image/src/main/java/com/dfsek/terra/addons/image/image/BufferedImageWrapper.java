package com.dfsek.terra.addons.image.image;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;


/**
 * Wraps a BufferedImage.
 * <p>
 * Fast path: 8-bit RGB / RGBA images stored as interleaved bytes in the sRGB colour space (what ImageIO gives for
 * ordinary 24/32-bit PNGs, e.g. heightmap and river tiles) are read straight from the byte array. That gives exactly
 * what {@link BufferedImage#getRGB} returns (no colour conversion happens for 8-bit sRGB, non-premultiplied), without
 * its per-pixel getDataElements + ColorModel calls and their temporary arrays. Every other image (palette, 1/2/4-bit,
 * grey, 16-bit, premultiplied, other colour spaces) still uses BufferedImage.getRGB.
 */
public class BufferedImageWrapper implements Image {

    private final BufferedImage image;
    private final int width, height;

    // fast path (data == null -> use image.getRGB)
    private final byte[] data;
    private final int scanline, pixelStride, base, offR, offG, offB, offA;

    public BufferedImageWrapper(BufferedImage image) {
        this.image = image;
        this.width = image.getWidth();
        this.height = image.getHeight();

        byte[] d = null;
        int sl = 0, ps = 0, b0 = 0, r = 0, g = 0, b = 0, a = -1;
        try {
            Raster raster = image.getRaster();
            ColorModel cm = image.getColorModel();
            if(raster.getDataBuffer() instanceof DataBufferByte buffer
               && buffer.getNumBanks() == 1
               && raster.getSampleModel() instanceof PixelInterleavedSampleModel sm
               && cm instanceof ComponentColorModel
               && cm.getColorSpace().isCS_sRGB()
               && cm.getColorSpace().getType() == ColorSpace.TYPE_RGB
               && !cm.isAlphaPremultiplied()
               && cm.getTransferType() == DataBuffer.TYPE_BYTE
               && (sm.getNumBands() == 3 && !cm.hasAlpha() || sm.getNumBands() == 4 && cm.hasAlpha())
               && allBits(cm, 8)
               && raster.getParent() == null
               && raster.getSampleModelTranslateX() == 0 && raster.getSampleModelTranslateY() == 0) {
                int[] off = sm.getBandOffsets();
                d = buffer.getData();
                sl = sm.getScanlineStride();
                ps = sm.getPixelStride();
                b0 = buffer.getOffset();
                r = off[0];
                g = off[1];
                b = off[2];
                a = sm.getNumBands() == 4 ? off[3] : -1;
            }
        } catch(RuntimeException ignored) {
            d = null;
        }
        this.data = d;
        this.scanline = sl;
        this.pixelStride = ps;
        this.base = b0;
        this.offR = r;
        this.offG = g;
        this.offB = b;
        this.offA = a;
    }

    private static boolean allBits(ColorModel cm, int bits) {
        for(int s : cm.getComponentSize()) if(s != bits) return false;
        return true;
    }

    @Override
    public int getRGB(int x, int y) {
        byte[] d = data;
        if(d == null) return image.getRGB(x, y);
        if(x < 0 || y < 0 || x >= width || y >= height)
            return image.getRGB(x, y); // same exception as before
        int i = base + y * scanline + x * pixelStride;
        int alpha = offA < 0 ? 0xFF000000 : (d[i + offA] & 0xFF) << 24;
        return alpha | (d[i + offR] & 0xFF) << 16 | (d[i + offG] & 0xFF) << 8 | (d[i + offB] & 0xFF);
    }

    /** True if reads skip BufferedImage.getRGB (for logging / tests). */
    public boolean isFastPath() {
        return data != null;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }
}
