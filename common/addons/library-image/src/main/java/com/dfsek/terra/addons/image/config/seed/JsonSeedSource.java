package com.dfsek.terra.addons.image.config.seed;

import com.dfsek.seismic.type.vector.Vector2;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class JsonSeedSource implements SeedSource {
    private final List<Vector2> points = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    public JsonSeedSource(Path file, double defaultWeight) {
        try(Reader reader = Files.newBufferedReader(file)) {
            JSONArray array = (JSONArray) new JSONParser().parse(reader);
            for(Object o : array) {
                JSONObject entry = (JSONObject) o;
                double x = ((Number) entry.get("x")).doubleValue();
                double z = ((Number) entry.get("z")).doubleValue();

                // "radius" is the natural term for real-world seed data (e.g. a volcano's
                // basal radius); it maps directly onto the sampler's weighted-distance
                // weight, since a larger radius means the seed's Voronoi cell should
                // reach further. "weight" is accepted as an alias for hand-authored points.
                double weight = defaultWeight;
                if(entry.get("radius") != null) {
                    weight = ((Number) entry.get("radius")).doubleValue();
                } else if(entry.get("weight") != null) {
                    weight = ((Number) entry.get("weight")).doubleValue();
                }

                points.add(Vector2.of(x, z));
                weights.add(weight);
            }
        } catch(IOException | ParseException e) {
            throw new RuntimeException("Failed to load seed data from " + file, e);
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