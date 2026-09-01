package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.seismic.type.vector.Vector2;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dfsek.terra.addons.image.noisesampler.seed.PointListSeedSource;
import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;


public class PointSeedSourceTemplate implements ObjectTemplate<SeedSource> {
    @Value("points")
    protected List<Map<String, Object>> points;

    @Override
    public SeedSource get() {
        List<Vector2> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for(Map<String, Object> entry : points) {
            double x = ((Number) entry.get("x")).doubleValue();
            double z = ((Number) entry.get("z")).doubleValue();
            double weight = entry.get("weight") != null ? ((Number) entry.get("weight")).doubleValue() : 1.0;
            vectors.add(Vector2.of(x, z));
            weights.add(weight);
        }
        return new PointListSeedSource(vectors, weights);
    }
}