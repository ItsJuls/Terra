package com.dfsek.terra.addons.image.noisesampler;

import com.dfsek.seismic.type.sampler.Sampler;
import com.dfsek.seismic.type.vector.Vector2;

import java.util.List;
import java.util.Map;


/**
 * Real-world volcanoes (and other "spot" landforms) from a list of seed points with per-point attributes.
 * <p>
 * Unlike PLACED_CELLULAR, which only knows the single nearest seed, this sampler adds up every seed whose
 * footprint covers the queried block, so neighbouring and overlapping volcanoes (Kilimanjaro's three cones,
 * the Andes, Java) blend without seams. Seeds are found through a coarse grid, so the ~99% of the world that
 * is nowhere near a volcano costs one hash lookup.
 * <p>
 * Per-point attributes (all in blocks unless noted; missing ones use the defaults in brackets):
 * <ul>
 *   <li>radius  - footprint radius (the seed weight)</li>
 *   <li>shape   - 1 stratovolcano, 2 shield, 3 caldera, 4 lava dome, 5 cinder cone, 6 maar, 7 tuff ring, 8 tuya [1]</li>
 *   <li>height  - height added at the centre [0]; 0 for volcanoes the heightmap already shows</li>
 *   <li>crater  - crater / caldera radius [0 = none]</li>
 *   <li>craterDepth - how deep the crater is carved [0]</li>
 *   <li>rim     - extra rim height for calderas, maars and tuff rings [0]</li>
 *   <li>angle, stretch - elongated footprints (fissures, crater rows): radians, length/width ratio [0, 1]</li>
 * </ul>
 * Returns (see {@link Output}): HEIGHT (blocks to add to the terrain), INFLUENCE (0..1, 1 at a centre),
 * RATIO (distance / radius of the closest footprint), CRATER (distance / crater radius, for crater floors and lakes)
 * or ATTRIBUTE (an attribute of the dominant seed, e.g. "activity" for biomes).
 */
public class VolcanoFieldSampler implements Sampler {
    public enum Output {
        HEIGHT,
        INFLUENCE,
        RATIO,
        CRATER,
        ATTRIBUTE
    }

    public static final int STRATO = 1, SHIELD = 2, CALDERA = 3, DOME = 4, CINDER = 5, MAAR = 6, TUFF_RING = 7, TUYA = 8;

    private static final int CELL_SHIFT = 6; // 64-block grid cells

    private final int n;
    private final double[] px, pz, radius, height, crater, craterDepth, rim, cos, sin, stretch;
    private final int[] shape;
    private final double[] attribute;
    // flat grid over the points' bounding box (CSR: cell -> range in cellItems); no boxing, one array read per query
    private final int gx0, gz0, gw, gh;
    private final int[] cellStart, cellItems;
    private final Output output;
    private final double defaultValue;
    private final double limit;

    public VolcanoFieldSampler(List<Vector2> points, List<Double> weights, List<Map<String, Double>> attributes,
                               Output output, String attributeName, double defaultValue, double limit) {
        this.output = output;
        this.defaultValue = defaultValue;
        this.limit = limit;
        this.n = points.size();
        px = new double[n];
        pz = new double[n];
        radius = new double[n];
        height = new double[n];
        crater = new double[n];
        craterDepth = new double[n];
        rim = new double[n];
        cos = new double[n];
        sin = new double[n];
        stretch = new double[n];
        shape = new int[n];
        attribute = new double[n];

        int minCx = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE, maxCz = Integer.MIN_VALUE;
        int[][] span = new int[n][];
        for(int i = 0; i < n; i++) {
            Map<String, Double> a = attributes != null && i < attributes.size() && attributes.get(i) != null
                                    ? attributes.get(i) : Map.of();
            px[i] = points.get(i).getX();
            pz[i] = points.get(i).getZ();
            radius[i] = Math.max(1e-3, weights.get(i));
            shape[i] = (int) Math.round(a.getOrDefault("shape", (double) STRATO));
            height[i] = a.getOrDefault("height", 0.0);
            crater[i] = a.getOrDefault("crater", 0.0);
            craterDepth[i] = a.getOrDefault("craterDepth", 0.0);
            rim[i] = a.getOrDefault("rim", 0.0);
            double ang = a.getOrDefault("angle", 0.0);
            cos[i] = Math.cos(ang);
            sin[i] = Math.sin(ang);
            stretch[i] = Math.max(1.0, a.getOrDefault("stretch", 1.0));
            attribute[i] = attributeName == null ? 0 : a.getOrDefault(attributeName, defaultValue);

            double reach = radius[i] * stretch[i] * Math.max(1.0, limit);
            int cx0 = (int) Math.floor(px[i] - reach) >> CELL_SHIFT, cx1 = (int) Math.floor(px[i] + reach) >> CELL_SHIFT;
            int cz0 = (int) Math.floor(pz[i] - reach) >> CELL_SHIFT, cz1 = (int) Math.floor(pz[i] + reach) >> CELL_SHIFT;
            span[i] = new int[]{ cx0, cx1, cz0, cz1 };
            minCx = Math.min(minCx, cx0);
            maxCx = Math.max(maxCx, cx1);
            minCz = Math.min(minCz, cz0);
            maxCz = Math.max(maxCz, cz1);
        }
        if(n == 0) {
            gx0 = gz0 = gw = gh = 0;
            cellStart = new int[1];
            cellItems = new int[0];
            return;
        }
        gx0 = minCx;
        gz0 = minCz;
        gw = maxCx - minCx + 1;
        gh = maxCz - minCz + 1;
        int[] count = new int[gw * gh + 1];
        for(int[] sp : span)
            for(int cz = sp[2]; cz <= sp[3]; cz++)
                for(int cx = sp[0]; cx <= sp[1]; cx++) count[(cz - gz0) * gw + (cx - gx0) + 1]++;
        for(int c = 1; c < count.length; c++) count[c] += count[c - 1];
        cellStart = count.clone();
        cellItems = new int[count[count.length - 1]];
        for(int i = 0; i < n; i++) {
            int[] sp = span[i];
            for(int cz = sp[2]; cz <= sp[3]; cz++)
                for(int cx = sp[0]; cx <= sp[1]; cx++) cellItems[count[(cz - gz0) * gw + (cx - gx0)]++] = i;
        }
    }

    private static double smoothstep(double e0, double e1, double x) {
        if(e0 == e1) return x < e0 ? 0 : 1;
        double t = Math.min(1, Math.max(0, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** distance from seed i in its own (rotated, stretched) frame */
    private double distance(int i, double x, double z) {
        double dx = x - px[i], dz = z - pz[i];
        if(stretch[i] == 1.0) return Math.sqrt(dx * dx + dz * dz);
        double u = dx * cos[i] + dz * sin[i];
        double v = -dx * sin[i] + dz * cos[i];
        u /= stretch[i];
        return Math.sqrt(u * u + v * v);
    }

    /** raised part of seed i at distance d (>= 0) */
    private double raise(int i, double d) {
        double r = radius[i], s = d / r;
        if(s >= 1) return 0;
        double h = height[i], c = crater[i];
        switch(shape[i]) {
            case SHIELD:
                return h * Math.pow(1 - Math.pow(s, 1.8), 1.2);
            case CALDERA: {
                // broad edifice plus a rim around the caldera
                double base = h * Math.pow(1 - s, 1.3);
                return base + ring(d, c, r, rim[i]);
            }
            case DOME:
                return h * Math.pow(1 - s * s * s, 0.6);
            case CINDER:
                return h * Math.pow(1 - s, 1.1);
            case MAAR:
            case TUFF_RING:
                return ring(d, c, r, Math.max(rim[i], h));
            case TUYA:
                return h * (1 - smoothstep(0.72, 1.0, s)) * (1 + 0.04 * (1 - s));
            case STRATO:
            default:
                return h * Math.pow(1 - s, 1.25);
        }
    }

    /** ring wall: rises steeply from the crater edge c to height top, falls off gently to the outer radius r */
    private static double ring(double d, double c, double r, double top) {
        if(top <= 0 || c <= 0) return 0;
        if(d < c) return top * smoothstep(c * 0.8, c, d);
        return top * Math.pow(1 - smoothstep(c, r, d), 1.5);
    }

    /** carved part of seed i (<= 0) */
    private double carve(int i, double d) {
        double c = crater[i], cd = craterDepth[i];
        if(c <= 0 || cd <= 0 || d >= c) return 0;
        double q = d / c;
        return switch(shape[i]) {
            // flat floors with steep walls
            case CALDERA, MAAR, TUFF_RING, SHIELD -> -cd * (1 - smoothstep(0.75, 1.0, q));
            // bowl-shaped summit craters
            default -> -cd * (1 - q * q);
        };
    }

    @Override
    public double getSample(long seed, double x, double z) {
        int cx = ((int) Math.floor(x) >> CELL_SHIFT) - gx0, cz = ((int) Math.floor(z) >> CELL_SHIFT) - gz0;
        int from = 0, to = 0;
        if(cx >= 0 && cz >= 0 && cx < gw && cz < gh) {
            int c = cz * gw + cx;
            from = cellStart[c];
            to = cellStart[c + 1];
        }
        if(from == to) {
            return switch(output) {
                case HEIGHT, INFLUENCE -> 0;
                case RATIO, CRATER, ATTRIBUTE -> defaultValue;
            };
        }
        switch(output) {
            case HEIGHT: {
                double up = 0, down = 0;
                for(int k = from; k < to; k++) {
                    int i = cellItems[k];
                    double d = distance(i, x, z);
                    if(d >= radius[i]) continue;
                    up = Math.max(up, raise(i, d));
                    down = Math.min(down, carve(i, d));
                }
                return up + down;
            }
            case INFLUENCE: {
                double best = 0;
                for(int k = from; k < to; k++) best = Math.max(best, 1 - distance(cellItems[k], x, z) / radius[cellItems[k]]);
                return Math.max(0, best);
            }
            case RATIO: {
                double best = Double.MAX_VALUE;
                for(int k = from; k < to; k++) best = Math.min(best, distance(cellItems[k], x, z) / radius[cellItems[k]]);
                return best < limit ? best : defaultValue;
            }
            case CRATER: {
                double best = Double.MAX_VALUE;
                for(int k = from; k < to; k++) {
                    int i = cellItems[k];
                    if(crater[i] > 0) best = Math.min(best, distance(i, x, z) / crater[i]);
                }
                return best < limit ? best : defaultValue;
            }
            case ATTRIBUTE:
            default: {
                double best = Double.MAX_VALUE;
                int which = -1;
                for(int k = from; k < to; k++) {
                    int i = cellItems[k];
                    double s = distance(i, x, z) / radius[i];
                    if(s < best) {
                        best = s;
                        which = i;
                    }
                }
                return which >= 0 && best < limit ? attribute[which] : defaultValue;
            }
        }
    }

    @Override
    public double getSample(long seed, double x, double y, double z) {
        return getSample(seed, x, z);
    }
}
