package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.seismic.algorithms.sampler.noise.cellular.PlacedCellularAttributeSampler;
import com.dfsek.seismic.type.sampler.Sampler;
import com.dfsek.seismic.type.spatial.KDTree;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;


public class PlacedCellularNoiseAttributeTemplate implements ObjectTemplate<Sampler> {

    // required on every sampler per Tectonic's generic loading convention,
    // even though this class doesn't branch on it internally
    @Value("dimensions")
    @Default
    protected int dimensions = 2;

    @Value("frequency")
    @Default
    protected double frequency = 1.0;

    @Value("salt")
    @Default
    protected long salt = 0;

    @Value("attribute")
    protected String attribute;

    @Value("default")
    @Default
    protected double defaultValue = 0.0;

    @Value("seeds")
    protected SeedSource seedSource;

    @Override
    public Sampler get() {
        KDTree tree = new KDTree(seedSource.getPoints(), seedSource.getWeights(), seedSource.getAttributes());
        return new PlacedCellularAttributeSampler(frequency, salt, tree, attribute, defaultValue);
    }
}