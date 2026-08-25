package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.seismic.type.vector.Vector2;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import java.util.ArrayList;
import java.util.List;

import com.dfsek.terra.addons.image.noisesampler.seed.PointListSeedSource;
import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;


public class PointSeedSourceTemplate implements ObjectTemplate<SeedSource> {
    @Value("points")
    protected List<PointEntry> points;

    @Override
    public SeedSource get() {
        List<Vector2> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for(PointEntry entry : points) {
            vectors.add(Vector2.of(entry.x, entry.z));
            weights.add(entry.weight);
        }
        return new PointListSeedSource(vectors, weights);
    }

    public static class PointEntry {
        double x;
        double z;
        double weight;
    }

    public static class PointEntryTemplate implements ObjectTemplate<PointEntry> {
        @Value("x")
        private double x;

        @Value("z")
        private double z;

        @Value("weight")
        @Default
        private double weight = 1.0;

        @Override
        public PointEntry get() {
            PointEntry entry = new PointEntry();
            entry.x = x;
            entry.z = z;
            entry.weight = weight;
            return entry;
        }
    }
}