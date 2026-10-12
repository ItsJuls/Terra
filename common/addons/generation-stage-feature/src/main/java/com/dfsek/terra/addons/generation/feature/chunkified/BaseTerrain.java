/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.generation.feature.chunkified;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.world.World;
import com.dfsek.terra.api.world.chunk.generation.ProtoChunk;


/**
 * The terrain the chunk generator produces before any generation stage runs, for any chunk, generated or not.
 * <p>
 * Chunkified structures read this outside the chunk being generated, so every chunk sees the same terrain no matter which
 * neighbours exist yet.
 */
public class BaseTerrain {
    private final Cache<ChunkKey, TerrainChunk> cache;
    private final BlockState air;

    public BaseTerrain(BlockState air, int cacheSize) {
        this.air = air;
        this.cache = Caffeine.newBuilder()
            .maximumSize(cacheSize)
            .expireAfterAccess(Duration.ofMinutes(2))
            .build();
    }

    public BlockState get(World world, int x, int y, int z) {
        if(y < world.getMinHeight() || y >= world.getMaxHeight()) return air;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return cache.get(ChunkKey.of(world, chunkX, chunkZ), key -> {
                TerrainChunk chunk = new TerrainChunk(air, world.getMinHeight(), world.getMaxHeight());
                world.getGenerator().generateChunkData(chunk, world, world.getBiomeProvider(), chunkX, chunkZ);
                return chunk;
            })
            .getBlock(x & 15, y, z & 15);
    }

    private static final class TerrainChunk implements ProtoChunk {
        private final BlockState air;
        private final int minHeight;
        private final int maxHeight;
        private final BlockState[][] sections; // null sections are all air

        private TerrainChunk(BlockState air, int minHeight, int maxHeight) {
            this.air = air;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.sections = new BlockState[(maxHeight - minHeight + 15) >> 4][];
        }

        @Override
        public void setBlock(int x, int y, int z, @NotNull BlockState blockState) {
            if(y < minHeight || y >= maxHeight || ((x | z) & ~15) != 0) return;
            int section = (y - minHeight) >> 4;
            BlockState[] blocks = sections[section];
            if(blocks == null) {
                if(blockState.isAir()) return;
                blocks = sections[section] = new BlockState[4096];
            }
            blocks[index(x, y - minHeight, z)] = blockState;
        }

        @Override
        public @NotNull BlockState getBlock(int x, int y, int z) {
            if(y < minHeight || y >= maxHeight || ((x | z) & ~15) != 0) return air;
            BlockState[] blocks = sections[(y - minHeight) >> 4];
            if(blocks == null) return air;
            BlockState state = blocks[index(x, y - minHeight, z)];
            return state == null ? air : state;
        }

        private static int index(int x, int relativeY, int z) {
            return ((relativeY & 15) << 8) | (z << 4) | x;
        }

        @Override
        public int getMaxHeight() {
            return maxHeight;
        }

        @Override
        public Object getHandle() {
            return this;
        }
    }
}
