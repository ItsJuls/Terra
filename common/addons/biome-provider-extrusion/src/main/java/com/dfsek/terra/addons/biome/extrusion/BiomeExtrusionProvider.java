package com.dfsek.terra.addons.biome.extrusion;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.dfsek.terra.addons.biome.extrusion.api.Extrusion;
import com.dfsek.terra.addons.biome.extrusion.utils.ExtrusionPipeline;
import com.dfsek.terra.addons.biome.extrusion.utils.ExtrusionPipelineFactory;
import com.dfsek.terra.api.util.Column;
import com.dfsek.terra.api.world.biome.Biome;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;


public class BiomeExtrusionProvider implements BiomeProvider {
    /**
     * perf patch: extruded columns are shared between the terrain interpolator, the block loop, the carver and every
     * feature stage (and neighbouring chunks) through this cache, so each (x, z, 4-block cell) runs the extrusion
     * samplers once instead of hundreds of times. 0 disables it. ~0.5 KB per column.
     */
    private static final int COLUMN_CACHE = Integer.getInteger("terra.extrusion.columnCache", 32768);
    /**
     * Horizontal cell for the extrusion samplers (1, 2 or 4 blocks; default 4 = Minecraft's biome cell). A block whose
     * base biome is the same as its cell corner's shares that corner's extruded column; blocks where the base biome
     * changes inside the cell keep their own column, so base-biome borders stay exact.
     */
    private static final int CELL_MASK = ~(Math.max(1, Math.min(4, Integer.getInteger("terra.extrusion.cell", 4))) - 1);

    public final ExtrusionPipeline pipeline;
    private final BiomeProvider delegate;
    private final Set<Biome> biomes;
    private final int resolution;
    private final Cache<Long, BaseBiomeColumn> columns;

    public BiomeExtrusionProvider(BiomeProvider delegate, List<Extrusion> extrusions, int resolution) {
        this.delegate = delegate;
        this.biomes = delegate.stream().collect(Collectors.toSet());
        extrusions.forEach(e -> biomes.addAll(e.getBiomes()));

        this.pipeline = ExtrusionPipelineFactory.create(extrusions);

        this.resolution = resolution;
        this.columns = COLUMN_CACHE > 0 ? Caffeine.newBuilder().maximumSize(COLUMN_CACHE).build() : null;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public Biome getBiome(int x, int y, int z, long seed) {
        if(columns != null) {
            Column<Biome> c = getColumn(x, z, seed, Integer.MIN_VALUE, Integer.MIN_VALUE);
            if(c != null) {
                return c.get(y);
            }
        }
        Biome delegated = delegate.getBiome(x, y, z, seed);
        return pipeline.extrude(delegated, x, BaseBiomeColumn.QUANTIZE ? y & ~3 : y, z, seed);
    }

    private BaseBiomeColumn cached(int x, int z, long seed, int min, int max, Biome base, long k) {
        BaseBiomeColumn c = columns.getIfPresent(k);
        if(c != null && c.base == base && (min == Integer.MIN_VALUE ? c.seed == seed : c.matches(seed, min, max))) {
            return c;
        }
        if(min == Integer.MIN_VALUE) {
            return null; // getBiome: no world height known, do not create
        }
        c = new BaseBiomeColumn(this, base, min, max, x, z, seed);
        columns.put(k, c);
        return c;
    }

    @Override
    public Column<Biome> getColumn(int x, int z, long seed, int min, int max) {
        if(columns != null) {
            Optional<Biome> base = delegate.getBaseBiome(x, z, seed);
            if(base.isEmpty()) {
                return min == Integer.MIN_VALUE ? null : BiomeProvider.super.getColumn(x, z, seed, min, max);
            }
            int cx = x & CELL_MASK;
            int cz = z & CELL_MASK;
            if(cx != x || cz != z) {
                Optional<Biome> cellBase = delegate.getBaseBiome(cx, cz, seed);
                if(cellBase.isPresent() && cellBase.get() == base.get()) {
                    BaseBiomeColumn c = cached(cx, cz, seed, min, max, base.get(), key(cx, cz));
                    return c == null ? null : c.at(x, z);
                }
                // base biome changes inside the cell: own column (separate key space)
                return cached(x, z, seed, min, max, base.get(), key(x, z) ^ Long.MIN_VALUE);
            }
            return cached(x, z, seed, min, max, base.get(), key(x, z));
        }
        return delegate.getBaseBiome(x, z, seed)
            .map(b -> (Column<Biome>) new BaseBiomeColumn(this, b, min, max, x, z, seed))
            .orElseGet(() -> BiomeProvider.super.getColumn(x, z, seed, min, max));
    }

    @Override
    public Optional<Biome> getBaseBiome(int x, int z, long seed) {
        return delegate.getBaseBiome(x, z, seed);
    }

    @Override
    public Iterable<Biome> getBiomes() {
        return biomes;
    }

    @Override
    public int resolution() {
        return resolution;
    }

    public BiomeProvider getDelegate() {
        return delegate;
    }
}
