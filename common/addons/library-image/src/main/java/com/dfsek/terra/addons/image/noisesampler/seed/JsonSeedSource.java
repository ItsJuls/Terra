package com.dfsek.terra.addons.image.noisesampler.seed;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Seed points from a JSON array of objects: {"x": .., "z": .., "radius": .., ...}.
 * <p>
 * "radius" (or its alias "weight") becomes the point's weight. Every other numeric field
 * (e.g. "shape", "height", "crater", "activity") becomes a named per-point attribute, readable with
 * PLACED_CELLULAR_ATTRIBUTE or VOLCANO_FIELD. Non-numeric fields (names, notes) are ignored, so the
 * file can carry labels for humans.
 */
public class JsonSeedSource implements SeedSource {
    private final List<Vector2> points = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private final List<Map<String, Double>> attributes = new ArrayList<>();

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
                if(entry.get("radius") instanceof Number r) {
                    weight = r.doubleValue();
                } else if(entry.get("weight") instanceof Number w) {
                    weight = w.doubleValue();
                }

                Map<String, Double> attrs = new HashMap<>();
                for(Object k : entry.keySet()) {
                    String key = String.valueOf(k);
                    if(key.equals("x") || key.equals("z") || key.equals("weight")) continue;
                    if(entry.get(k) instanceof Number n) attrs.put(key, n.doubleValue());
                }

                points.add(Vector2.of(x, z));
                weights.add(weight);
                attributes.add(attrs);
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

    @Override
    public List<Map<String, Double>> getAttributes() {
        return attributes;
    }
}
