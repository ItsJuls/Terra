package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.seismic.type.sampler.Sampler;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import java.util.Locale;

import com.dfsek.terra.addons.image.noisesampler.VolcanoFieldSampler;
import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;


/**
 * type: VOLCANO_FIELD
 * seeds:        any seed source; JSON carries the per-volcano attributes (see VolcanoFieldSampler)
 * return:       HEIGHT (default) | INFLUENCE | RATIO | CRATER | ATTRIBUTE
 * attribute:    attribute name for return: ATTRIBUTE
 * default:      value outside every footprint for RATIO / CRATER / ATTRIBUTE
 * limit:        RATIO / CRATER / ATTRIBUTE only count seeds closer than limit * radius (default 1)
 */
@SuppressWarnings("FieldMayBeFinal")
public class VolcanoFieldSamplerTemplate implements ObjectTemplate<Sampler> {
    @Value("dimensions")
    @Default
    protected int dimensions = 2;

    @Value("seeds")
    protected SeedSource seedSource;

    @Value("return")
    @Default
    protected String output = "HEIGHT";

    @Value("attribute")
    @Default
    protected String attribute = null;

    @Value("default")
    @Default
    protected double defaultValue = 0;

    @Value("limit")
    @Default
    protected double limit = 1;

    @Override
    public Sampler get() {
        return new VolcanoFieldSampler(seedSource.getPoints(), seedSource.getWeights(), seedSource.getAttributes(),
            VolcanoFieldSampler.Output.valueOf(output.toUpperCase(Locale.ROOT)), attribute, defaultValue, limit);
    }
}
