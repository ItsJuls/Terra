package com.dfsek.terra.addons.biome.extrusion;

import com.dfsek.terra.api.util.Column;
import com.dfsek.terra.api.world.biome.Biome;


/**
 * A column of extruded biomes with memoised results (perf patch, Terra-Combined).
 * <p>
 * The noise-3d generator asks the same column for the same y many times: ChunkInterpolator blending reads every
 * neighbour column at every 4th y (up to 49 x per grid point), generateChunkData reads every block, the carver and
 * every feature stage read it again. Each call used to run the whole extrusion pipeline (all extrusion samplers).
 * <p>
 * Biomes are quantised to 4-block cells (y & ~3, the same grid as Minecraft's biome storage and Terra's 3D
 * interpolator), so a column runs the pipeline at most once per cell. -Dterra.extrusion.quantize=false keeps exact
 * per-block y (still memoised).
 */
class BaseBiomeColumn implements Column<Biome> {
    static final boolean QUANTIZE = !"false".equalsIgnoreCase(System.getProperty("terra.extrusion.quantize"));

    private final BiomeExtrusionProvider biomeProvider;
    final Biome base;
    final int min;
    final int max;

    private final int x;
    private final int z;
    final long seed;
    private final int offset;
    private final Biome[] cache;

    protected BaseBiomeColumn(BiomeExtrusionProvider biomeProvider, Biome base, int min, int max, int x, int z, long seed) {
        this.biomeProvider = biomeProvider;
        this.base = base;
        this.min = min;
        this.max = max;
        this.x = x;
        this.z = z;
        this.seed = seed;
        if(QUANTIZE) {
            this.offset = min >> 2;
            this.cache = new Biome[Math.max(1, (max >> 2) - (min >> 2) + 1)];
        } else {
            this.offset = min;
            this.cache = new Biome[Math.max(1, max - min + 1)];
        }
    }

    boolean matches(long seed, int min, int max) {
        return this.seed == seed && this.min == min && this.max == max;
    }

    /** This column's biomes seen from another block of the same cell (only getX / getZ differ). */
    Column<Biome> at(int bx, int bz) {
        if(bx == x && bz == z) return this;
        BaseBiomeColumn self = this;
        return new Column<>() {
            @Override public int getMinY() { return min; }
            @Override public int getMaxY() { return max; }
            @Override public int getX() { return bx; }
            @Override public int getZ() { return bz; }
            @Override public Biome get(int y) { return self.get(y); }
        };
    }

    @Override
    public int getMinY() {
        return min;
    }

    @Override
    public int getMaxY() {
        return max;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public Biome get(int y) {
        int i = (QUANTIZE ? y >> 2 : y) - offset;
        if(i < 0 || i >= cache.length) {
            return biomeProvider.pipeline.extrude(base, x, QUANTIZE ? y & ~3 : y, z, seed);
        }
        Biome b = cache[i];
        if(b == null) {
            b = biomeProvider.pipeline.extrude(base, x, QUANTIZE ? y & ~3 : y, z, seed);
            cache[i] = b;
        }
        return b;
    }
}
