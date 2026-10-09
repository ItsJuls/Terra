package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.seismic.type.vector.Vector2;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dfsek.terra.addons.image.noisesampler.seed.PointListSeedSource;
import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;


public class PointSeedSourceTemplate implements ObjectTemplate<SeedSource> {
    @Value("points")
    protected List<Map<String, Double>> points;

    @Override
    public SeedSource get() {
        List<Vector2> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<Map<String, Double>> attributes = new ArrayList<>();

        for(Map<String, Double> entry : points) {
            double x = entry.get("x");
            double z = entry.get("z");
            double weight = entry.getOrDefault("weight", 1.0);
            vectors.add(Vector2.of(x, z));
            weights.add(weight);

            // Any other keys on this point (radius, height, ...) become named
            // attributes readable via placedSpotRadius(x,z) / placedSpotHeight(x,z)
            Map<String, Double> attrs = new HashMap<>();
            for(Map.Entry<String, Double> field : entry.entrySet()) {
                String key = field.getKey();
                if(!key.equals("x") && !key.equals("z") && !key.equals("weight")) {
                    attrs.put(key, field.getValue());
                }
            }
            attributes.add(attrs);
        }

        return new PointListSeedSource(vectors, weights, attributes);
    }
}