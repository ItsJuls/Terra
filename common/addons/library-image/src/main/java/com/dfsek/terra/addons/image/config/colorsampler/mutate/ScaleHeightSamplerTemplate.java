package com.dfsek.terra.addons.image.config.colorsampler.mutate;

import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.terra.addons.image.colorsampler.ColorSampler;
import com.dfsek.terra.addons.image.colorsampler.mutate.ScaleHeightColorSampler;

public class ScaleHeightSamplerTemplate extends MutateColorSamplerTemplate {

    @Value("x")
    private double scaleX;

    @Value("z")
    private double scaleZ;

    @Value("interpolation")
    @Default
    private String interpolation = "BICUBIC";

    @Override
    public ColorSampler get() {
        return new ScaleHeightColorSampler(sampler, scaleX, scaleZ, interpolation);
    }
}