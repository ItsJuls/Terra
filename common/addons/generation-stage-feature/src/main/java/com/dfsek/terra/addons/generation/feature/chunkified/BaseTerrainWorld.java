/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.generation.feature.chunkified;

import com.dfsek.terra.api.block.entity.BlockEntity;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.entity.Entity;
import com.dfsek.terra.api.entity.EntityType;
import com.dfsek.terra.api.world.WritableWorld;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;
import com.dfsek.terra.api.world.chunk.generation.ChunkGenerator;
import com.dfsek.terra.api.world.chunk.generation.ProtoWorld;


/**
 * Read-only view of {@link BaseTerrain}, used to locate chunkified structures. Writes are ignored.
 */
final class BaseTerrainWorld implements WritableWorld {
    private final ProtoWorld world;
    private final BaseTerrain terrain;

    BaseTerrainWorld(ProtoWorld world, BaseTerrain terrain) {
        this.world = world;
        this.terrain = terrain;
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        return terrain.get(world, x, y, z);
    }

    @Override
    public BlockEntity getBlockEntity(int x, int y, int z) {
        return null;
    }

    @Override
    public void setBlockState(int x, int y, int z, BlockState data, boolean physics) {
    }

    @Override
    public Entity spawnEntity(double x, double y, double z, EntityType entityType) {
        return null;
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
