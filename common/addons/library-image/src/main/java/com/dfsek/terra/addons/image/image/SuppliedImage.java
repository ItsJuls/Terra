package com.dfsek.terra.addons.image.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Supplier;


/**
 * An image that is loaded (and, with images.cache.timeout > 0, unloaded) by the pack's image cache.
 * <p>
 * The supplier (a Caffeine cache lookup with expire-after-access) used to be called for every pixel. With 32 world
 * generation threads reading the same few tiles, every lookup also rewrote the cache entry's access time, so the
 * threads fought over the same memory: in a spark profile this lookup was the single largest cost of chunk
 * generation. Now the image is remembered here and the cache is asked again at most twice a second per image, which
 * still keeps a used image from expiring. The image is held through a WeakReference, so once the cache drops an
 * unused image nothing here keeps it in memory.
 * <p>
 * Width and height come from the file header when a size supplier is given, so asking for the size (as
 * StitchedImage does for every tile while the pack loads) does not decode the whole image. Decoding every tile of
 * every stitched image at once needed more than 20 GB of heap.
 */
public class SuppliedImage implements Image {

    /** Bumped about every 0.5 s by a daemon thread; an image re-asks its supplier when this has changed. */
    private static volatile long epoch;

    static {
        Thread clock = new Thread(() -> {
            while(true) {
                try {
                    Thread.sleep(500);
                } catch(InterruptedException e) {
                    return;
                }
                epoch++;
            }
        }, "Terra image cache clock");
        clock.setDaemon(true);
        clock.start();
    }

    private final Supplier<Image> imageSupplier;
    private final Supplier<int[]> sizeSupplier;
    private volatile WeakReference<Image> ref = new WeakReference<>(null);
    private volatile long seenEpoch = -1;
    private volatile int width = -1, height = -1;

    public SuppliedImage(Supplier<Image> imageSupplier) {
        this(imageSupplier, null);
    }

    /** @param sizeSupplier returns {width, height} without decoding the image, or null to decode for the size */
    public SuppliedImage(Supplier<Image> imageSupplier, Supplier<int[]> sizeSupplier) {
        this.imageSupplier = imageSupplier;
        this.sizeSupplier = sizeSupplier;
    }

    /** Reads an image file's width and height from its header (no pixel decoding). */
    public static int[] headerSize(Path file) {
        try(InputStream raw = Files.newInputStream(file); ImageInputStream in = ImageIO.createImageInputStream(raw)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if(!readers.hasNext()) throw new IOException("No image reader for " + file);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{ reader.getWidth(0), reader.getHeight(0) };
            } finally {
                reader.dispose();
            }
        } catch(IOException e) {
            throw new UncheckedIOException("Could not read the size of " + file, e);
        }
    }

    private void loadSize() {
        int[] size = sizeSupplier != null ? sizeSupplier.get() : null;
        if(size == null) {
            Image img = image();
            size = new int[]{ img.getWidth(), img.getHeight() };
        }
        height = size[1];
        width = size[0];
    }

    private Image image() {
        long e = epoch;
        if(seenEpoch == e) {
            Image img = ref.get();
            if(img != null) return img;
        }
        Image img = imageSupplier.get();
        ref = new WeakReference<>(img);
        seenEpoch = e;
        return img;
    }

    @Override
    public int getRGB(int x, int y) {
        return image().getRGB(x, y);
    }

    @Override
    public int getWidth() {
        int w = width;
        if(w < 0) {
            loadSize();
            w = width;
        }
        return w;
    }

    @Override
    public int getHeight() {
        if(width < 0) loadSize();
        return height;
    }
}
