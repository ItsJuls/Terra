package com.dfsek.terra.addons.image.noisesampler.seed;

import com.dfsek.seismic.type.vector.Vector2;

import java.util.List;


public interface SeedSource {
    List<Vector2> getPoints();

    List<Double> getWeights();
}