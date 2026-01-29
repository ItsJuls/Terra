package com.dfsek.terra.addons.image.config.colorsampler.mutate;

import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.terra.addons.image.colorsampler.ColorSampler;
import com.dfsek.terra.addons.image.colorsampler.mutate.ScaleHeightColorSampler; // Import the class from step 1

public class ScaleHeightSamplerTemplate extends MutateColorSamplerTemplate {

    @Value("x")
    private double scaleX; // I suggest using double here for precision

    @Value("z")
    private double scaleZ;

    @Value("interpolation")
    @Default
    private String interpolation = "BICUBIC"; // Default to BICUBIC

    @Override
    public ColorSampler get() {
        // 'sampler' comes from the parent class MutateColorSamplerTemplate
        return new ScaleHeightColorSampler(sampler, scaleX, scaleZ, interpolation);
    }
}