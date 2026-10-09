package com.dfsek.terra.addons.image.noisesampler;

import com.dfsek.seismic.algorithms.sampler.noise.cellular.CellularStyleSampler;
import com.dfsek.seismic.algorithms.sampler.noise.cellular.PlacedCellularSampler;
import com.dfsek.seismic.algorithms.sampler.noise.simplex.OpenSimplex2Sampler;
import com.dfsek.seismic.type.DistanceFunction;
import com.dfsek.seismic.type.sampler.Sampler;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;
import com.dfsek.terra.api.config.meta.Meta;


@SuppressWarnings("FieldMayBeFinal")
public class PlacedCellularNoiseTemplate implements ObjectTemplate<Sampler> {
    @Value("frequency")
    @Default
    protected @Meta double frequency = 0.02d;

    @Value("salt")
    @Default
    protected @Meta long salt = 0;

    @Value("distance")
    @Default
    private @Meta DistanceFunction distanceFunction = DistanceFunction.EuclideanSq;

    @Value("return")
    @Default
    private CellularStyleSampler.@Meta CellularReturnType returnType = CellularStyleSampler.CellularReturnType.Distance;

    @Value("jitter")
    @Default
    private @Meta double jitter = 1.0D;

    @Value("lookup")
    @Default
    private @Meta Sampler lookup = new OpenSimplex2Sampler(0.02d, 0);

    @Value("salt-lookup")
    @Default
    private @Meta boolean saltLookup = true;

    @Value("seeds")
    protected SeedSource seedSource;

    @Override
    public Sampler get() {
        return new PlacedCellularSampler(frequency, salt, lookup, distanceFunction, returnType, jitter, saltLookup,
            seedSource.getPoints(), seedSource.getWeights());
    }
}