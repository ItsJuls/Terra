package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import com.dfsek.terra.addons.image.image.Image;
import com.dfsek.terra.addons.image.noisesampler.seed.ImageSeedSource;
import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;


public class ImageSeedSourceTemplate implements ObjectTemplate<SeedSource> {
    @Value("image")
    protected Image image;

    @Value("scale")
    @Default
    protected double scale = 1.0;

    @Value("origin-x")
    @Default
    protected double originX = 0;

    @Value("origin-z")
    @Default
    protected double originZ = 0;

    @Value("threshold")
    @Default
    protected int threshold = 128;

    @Value("weight")
    @Default
    protected double weight = 1.0;

    @Override
    public SeedSource get() {
        return new ImageSeedSource(image, scale, originX, originZ, threshold, weight);
    }
}