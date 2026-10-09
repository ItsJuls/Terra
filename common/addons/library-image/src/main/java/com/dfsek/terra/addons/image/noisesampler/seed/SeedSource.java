package com.dfsek.terra.addons.image.noisesampler.seed;

import com.dfsek.seismic.type.vector.Vector2;

import java.util.List;
import java.util.Map;


public interface SeedSource {
    List<Vector2> getPoints();

    List<Double> getWeights();

    /**
     * Per-point named attributes (e.g. "radius", "height"), aligned by index
     * with getPoints()/getWeights(). Returns null if this source doesn't
     * provide attributes - existing sources (ImageSeedSource, JsonSeedSource)
     * are unaffected by this addition since it's a default method.
     */
    default List<Map<String, Double>> getAttributes() {
        return null;
    }
}