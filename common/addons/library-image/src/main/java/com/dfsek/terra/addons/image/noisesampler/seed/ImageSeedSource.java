package com.dfsek.terra.addons.image.noisesampler.seed;

import com.dfsek.seismic.type.vector.Vector2;

import java.util.ArrayList;
import java.util.List;


import com.dfsek.terra.addons.image.image.Image;


public class ImageSeedSource implements SeedSource {
    private final List<Vector2> points = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    public ImageSeedSource(Image image, double scale, double originX, double originZ, int threshold, double defaultWeight) {
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                if(gray >= threshold) {
                    points.add(Vector2.of(originX + x * scale, originZ + y * scale));
                    weights.add(defaultWeight * (gray / 255.0));
                }
            }
        }
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