package com.dfsek.terra.mod.util;

import com.google.common.collect.MapMaker;
import net.minecraft.world.level.biome.Climate.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * Holder for hacky biome source seed workaround
 * <p>
 * Climate samplers are created on demand (e.g. once per generated chunk), so samplers are weakly referenced to avoid
 * leaking them.
 */
public class SeedHack {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedHack.class);
    private static final Map<Sampler, Long> seedMap = new MapMaker().weakKeys().makeMap();

    public static long getSeed(Sampler sampler) {
        Long seed = seedMap.get(sampler);
        if(seed == null) {
            throw new IllegalArgumentException("Sampler is not registered: " + sampler);
        }
        return seed;
    }

    public static void register(Sampler sampler, long seed) {
        LOGGER.trace("Registered seed {} to sampler {}", seed, System.identityHashCode(sampler));
        seedMap.put(sampler, seed);
    }
}
