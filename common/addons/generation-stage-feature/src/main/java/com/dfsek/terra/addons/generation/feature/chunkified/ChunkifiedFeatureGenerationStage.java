/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.generation.feature.chunkified;

import com.dfsek.seismic.type.sampler.Sampler;
import com.dfsek.seismic.type.vector.Vector3Int;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.dfsek.terra.addons.generation.feature.FeatureGenerationStage;
import com.dfsek.terra.addons.generation.feature.config.BiomeFeatures;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.properties.PropertyKey;
import com.dfsek.terra.api.structure.feature.Feature;
import com.dfsek.terra.api.world.chunk.generation.ProtoWorld;


/**
 * A feature stage whose structures may be any size up to {@code max-radius} blocks from their origin.
 * <p>
 * Minecraft only lets a chunk write to itself and its direct neighbours while it is decorated, which is what limits normal
 * feature stages. This stage places structures the way vanilla places its own structures instead: every chunk works out, from
 * the seed alone, which structures from this stage reach it, generates each of them and keeps only the blocks inside itself. The
 * result doesn't depend on the order chunks generate in and there is nothing to save, so it survives restarts.
 * <p>
 * Structure origins are located on the base terrain (before any generation stage), the same for every chunk. Each structure
 * generates once per chunk it covers, so this stage is meant for large, rare structures, not for trees or ores.
 */
public class ChunkifiedFeatureGenerationStage extends FeatureGenerationStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkifiedFeatureGenerationStage.class);

    private final int radius;
    private final BaseTerrain terrain;
    private final Cache<ChunkKey, List<StructureStart>> starts;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public ChunkifiedFeatureGenerationStage(Platform platform, String id, int resolution, PropertyKey<BiomeFeatures> biomeFeaturesKey,
                                            Sampler blendSampler, double blendAmplitude, int radius) {
        super(platform, id, resolution, biomeFeaturesKey, blendSampler, blendAmplitude);
        this.radius = radius;
        this.terrain = new BaseTerrain(platform.getWorldHandle().air(), 256);
        int chunks = 2 * Math.ceilDiv(radius, 16) + 2;
        this.starts = Caffeine.newBuilder()
            .maximumSize(Math.max(4096L, 4L * chunks * chunks))
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();
    }

    @Override
    public void populate(ProtoWorld world) {
        platform.getProfiler().push(profile);
        int chunkMinX = world.centerChunkX() << 4;
        int chunkMinZ = world.centerChunkZ() << 4;
        BaseTerrainWorld baseTerrain = new BaseTerrainWorld(world, terrain);

        // Every chunk visits origin chunks in the same order, so overlapping structures overwrite each other the same way everywhere.
        for(int originX = Math.floorDiv(chunkMinX - radius, 16); originX <= Math.floorDiv(chunkMinX + 15 + radius, 16); originX++) {
            for(int originZ = Math.floorDiv(chunkMinZ - radius, 16); originZ <= Math.floorDiv(chunkMinZ + 15 + radius, 16); originZ++) {
                for(StructureStart start : getStarts(baseTerrain, originX, originZ)) {
                    if(!start.mayIntersect(chunkMinX, chunkMinZ, radius)) continue;
                    generate(world, start);
                }
            }
        }
        platform.getProfiler().pop(profile);
    }

    private void generate(ProtoWorld world, StructureStart start) {
        platform.getProfiler().push(start.feature.getID());
        ChunkClippedWorld clipped = new ChunkClippedWorld(world, terrain, start, radius, () -> {
            if(warned.add(start.feature.getID())) {
                LOGGER.warn("Feature \"{}\" in chunkified stage \"{}\" placed a structure reaching more than {} blocks from its " +
                            "origin at ({}, {}, {}); the part past that is cut off. Increase the stage's max-radius to fit it.",
                    start.feature.getID(), getID(), radius, start.x, start.y, start.z);
            }
        });
        try {
            start.structure.generate(Vector3Int.of(start.x, start.y, start.z), clipped, random(start.seed), start.rotation);
            start.setBounds(clipped.bounds());
        } catch(RuntimeException e) {
            LOGGER.error("Failed to generate feature \"{}\" at ({}, {}, {}) in chunk ({}, {})", start.feature.getID(), start.x, start.y,
                start.z, world.centerChunkX(), world.centerChunkZ(), e);
        } finally {
            platform.getProfiler().pop(start.feature.getID());
        }
    }

    private List<StructureStart> getStarts(BaseTerrainWorld world, int chunkX, int chunkZ) {
        return starts.get(ChunkKey.of(world, chunkX, chunkZ), key -> {
            List<StructureStart> found = new ArrayList<>();
            Set<Placement> placed = new HashSet<>();
            forEachPlacement(world, chunkX, chunkZ, (feature, structure, x, y, z, seed, rotation) -> {
                // Locators that ignore biome height ranges (like TOP) return the same position once per range; generating an
                // identical structure again would only cost time and duplicate its entities.
                if(placed.add(new Placement(feature, x, y, z))) {
                    found.add(new StructureStart(feature, structure, x, y, z, seed, rotation));
                }
            });
            return List.copyOf(found);
        });
    }

    private record Placement(Feature feature, int x, int y, int z) {
    }
}
