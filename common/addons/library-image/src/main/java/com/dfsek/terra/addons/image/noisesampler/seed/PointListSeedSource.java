package com.dfsek.terra.addons.image.noisesampler.seed;

import com.dfsek.seismic.type.vector.Vector2;

import java.util.List;
import java.util.Map;


public class PointListSeedSource implements SeedSource {
    private final List<Vector2> points;
    private final List<Double> weights;
    private final List<Map<String, Double>> attributes;

    public PointListSeedSource(List<Vector2> points, List<Double> weights) {
        this(points, weights, null);
    }

    public PointListSeedSource(List<Vector2> points, List<Double> weights, List<Map<String, Double>> attributes) {
        this.points = points;
        this.weights = weights;
        this.attributes = attributes;
    }

    @Override
    public List<Vector2> getPoints() {
        return points;
    }

    @Override
    public List<Double> getWeights() {
        return weights;
    }

    @Override
    public List<Map<String, Double>> getAttributes() {
        return attributes;
    }
}