package com.dfsek.terra.addons.image.noisesampler.seed;

import com.dfsek.seismic.type.vector.Vector2;

import java.util.List;


public class PointListSeedSource implements SeedSource {
    private final List<Vector2> points;
    private final List<Double> weights;

    public PointListSeedSource(List<Vector2> points, List<Double> weights) {
        this.points = points;
        this.weights = weights;
    }

    @Override
    public List<Vector2> getPoints() {
        return points;
    }

    @Override
    public List<Double> getWeights() {
        return weights;
    }
}