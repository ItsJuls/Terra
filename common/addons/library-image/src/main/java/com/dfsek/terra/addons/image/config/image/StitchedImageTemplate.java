package com.dfsek.terra.addons.image.config.image;

import com.dfsek.tectonic.api.config.template.ValidatedConfigTemplate;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import com.dfsek.tectonic.api.exception.ValidationException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import com.dfsek.terra.addons.image.image.Image;
import com.dfsek.terra.addons.image.image.StitchedImage;
import com.dfsek.terra.api.config.ConfigPack;


public class StitchedImageTemplate implements ObjectTemplate<Image>, ValidatedConfigTemplate {

    private final ConfigPack pack;
    @Value("path-format")
    private String path;
    @Value("rows")
    private int rows;
    @Value("columns")
    private int cols;
    @Value("zero-indexed")
    @Default
    private boolean zeroIndexed = false;

    public StitchedImageTemplate(ConfigPack pack) {
        this.pack = pack;
    }

    @Override
    public Image get() {
        ImageCache.init(pack); // Initialize cache before parallel execution
        Image[][] grid = new Image[rows][cols];
        AtomicReference<Exception> error = new AtomicReference<>();

        // Parallel loading of images
        IntStream.range(0, rows * cols).parallel().forEach(index -> {
            if (error.get() != null) return; // Stop if error occurred
            int i = index / cols;
            int j = index % cols;
            try {
                grid[i][j] = ImageCache.load(getFormattedPath(i, j), pack);
            } catch (Exception e) {
                error.set(e);
            }
        });

        if (error.get() != null) {
            throw new RuntimeException("Failed to load stitched image tiles", error.get());
        }

        return new StitchedImage(grid, zeroIndexed);
    }

    private String getFormattedPath(int row, int column) {
        if(!zeroIndexed) {
            row++;
            column++;
        }
        return path.replaceFirst("\\{row}", String.valueOf(row)).replaceFirst("\\{column}", String.valueOf(column));
    }

    @Override
    public boolean validate() throws ValidationException {
        if(!path.contains("{row}"))
            throw new ValidationException("Path format does not contain sequence '{row}'");
        if(!path.contains("{column}"))
            throw new ValidationException("Path format does not contain sequence '{column}'");
        if(rows < 1)
            throw new ValidationException("Must have at least one row");
        if(cols < 1)
            throw new ValidationException("Must have at least one column");
        return true;
    }
}
