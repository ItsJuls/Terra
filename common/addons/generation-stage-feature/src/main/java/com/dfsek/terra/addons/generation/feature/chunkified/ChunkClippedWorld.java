/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.generation.feature.chunkified;

import java.util.HashMap;
import java.util.Map;

import com.dfsek.terra.api.block.entity.BlockEntity;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.entity.Entity;
import com.dfsek.terra.api.entity.EntityType;
import com.dfsek.terra.api.world.ServerWorld;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;
import com.dfsek.terra.api.world.chunk.generation.ChunkGenerator;
import com.dfsek.terra.api.world.chunk.generation.ProtoWorld;


/**
 * The world a chunkified structure generates into while one chunk is being populated.
 * <ul>
 *     <li>Blocks and entities inside the chunk go to the real world. Everything else is dropped; the chunks it belongs to
 *     place it when they generate.</li>
 *     <li>Reads inside the chunk see the real world. Reads elsewhere see what this structure has written so far on top of the
 *     {@link BaseTerrain base terrain}, which is the same no matter which chunk is generating, so structures make the same
 *     decisions in every chunk.</li>
 *     <li>Nothing further than the stage's radius from the structure's origin is written, so every chunk agrees on where the
 *     structure ends.</li>
 * </ul>
 */
final class ChunkClippedWorld implements ProtoWorld {
    private final ProtoWorld world;
    private final BaseTerrain terrain;
    private final StructureStart start;
    private final int chunkMinX, chunkMinZ;
    private final int radius;
    private final Runnable outOfRange;

    private Map<Long, BlockState> written;

    private int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

    ChunkClippedWorld(ProtoWorld world, BaseTerrain terrain, StructureStart start, int radius, Runnable outOfRange) {
        this.world = world;
        this.terrain = terrain;
        this.start = start;
        this.chunkMinX = world.centerChunkX() << 4;
        this.chunkMinZ = world.centerChunkZ() << 4;
        this.radius = radius;
        this.outOfRange = outOfRange;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private boolean inChunk(int x, int z) {
        return ((x - chunkMinX) & ~15) == 0 && ((z - chunkMinZ) & ~15) == 0;
    }

    /**
     * Whether a position may be written to, recording it in the structure's bounds if so.
     */
    private boolean claim(int x, int z) {
        if(Math.abs(x - start.x) > radius || Math.abs(z - start.z) > radius) {
            outOfRange.run();
            return false;
        }
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minZ = Math.min(minZ, z);
        maxZ = Math.max(maxZ, z);
        return true;
    }

    StructureStart.Bounds bounds() {
        if(minX > maxX) return StructureStart.Bounds.EMPTY;
        return new StructureStart.Bounds(minX, maxX, minZ, maxZ);
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState data, boolean physics) {
        if(!claim(x, z)) return;
        if(inChunk(x, z)) {
            world.setBlockState(x, y, z, data, physics);
        } else if(y >= world.getMinHeight() && y < world.getMaxHeight()) {
            if(written == null) written = new HashMap<>();
            written.put(key(x, y, z), data);
        }
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        if(inChunk(x, z)) return world.getBlockState(x, y, z);
        if(written != null) {
            BlockState state = written.get(key(x, y, z));
            if(state != null) return state;
        }
        return terrain.get(world, x, y, z);
    }

    @Override
    public BlockEntity getBlockEntity(int x, int y, int z) {
        return inChunk(x, z) ? world.getBlockEntity(x, y, z) : null;
    }

    @Override
    public Entity spawnEntity(double x, double y, double z, EntityType entityType) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if(!claim(blockX, blockZ) || !inChunk(blockX, blockZ)) return null;
        return world.spawnEntity(x, y, z, entityType);
    }

    @Override
    public int centerChunkX() {
        return world.centerChunkX();
    }

    @Override
    public int centerChunkZ() {
        return world.centerChunkZ();
    }

    @Override
    public ServerWorld getWorld() {
        return world.getWorld();
    }

    @Override
    public long getSeed() {
        return world.getSeed();
    }

    @Override
    public int getMaxHeight() {
        return world.getMaxHeight();
    }

    @Override
    public int getMinHeight() {
        return world.getMinHeight();
    }

    @Override
    public ChunkGenerator getGenerator() {
        return world.getGenerator();
    }

    @Override
    public BiomeProvider getBiomeProvider() {
        return world.getBiomeProvider();
    }

    @Override
    public ConfigPack getPack() {
        return world.getPack();
    }

    @Override
    public Object getHandle() {
        return world.getHandle();
    }
}
