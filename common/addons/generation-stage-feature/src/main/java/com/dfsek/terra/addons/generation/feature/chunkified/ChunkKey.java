/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.generation.feature.chunkified;

import com.dfsek.terra.api.world.info.WorldProperties;


/**
 * A chunk of a world. Worlds are told apart by the properties generation depends on, so dimensions sharing a pack and seed
 * share cached data.
 */
record ChunkKey(long seed, int minHeight, int maxHeight, int chunkX, int chunkZ) {
    static ChunkKey of(WorldProperties world, int chunkX, int chunkZ) {
        return new ChunkKey(world.getSeed(), world.getMinHeight(), world.getMaxHeight(), chunkX, chunkZ);
    }
}
