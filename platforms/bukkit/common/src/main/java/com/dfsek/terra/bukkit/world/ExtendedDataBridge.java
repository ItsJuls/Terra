/*
 * This file is part of Terra.
 *
 * Terra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Terra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Terra.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.dfsek.terra.bukkit.world;

import org.bukkit.RegionAccessor;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.Nullable;


/**
 * Access to block entity and entity NBT, which the Bukkit API cannot express. Implemented by the NMS module.
 */
public interface ExtendedDataBridge {
    /**
     * Parses SNBT into the platform's compound tag.
     *
     * @throws IllegalArgumentException if the data is not a valid compound
     */
    Object parseCompound(String snbt);

    /**
     * Prepares block entity data for a block, or returns {@code null} if the block has no block entity.
     */
    @Nullable Object prepareBlockEntityData(BlockData block, Object compound);

    /**
     * Prepares entity data for an entity type.
     */
    Object prepareEntityData(EntityType type, Object compound);

    /**
     * Loads block entity data into the block entity at the given position. The block must already be placed.
     */
    void loadBlockEntity(RegionAccessor world, int x, int y, int z, Object blockEntityData);

    /**
     * Loads block entity data into the block entity at the given position of a chunk being generated.
     */
    void loadBlockEntity(ChunkData chunk, int x, int y, int z, Object blockEntityData);

    /**
     * Spawns an entity (and its passengers) from entity data.
     */
    @Nullable Entity spawnEntity(RegionAccessor world, double x, double y, double z, Object entityData);

    static @Nullable ExtendedDataBridge get() {
        return Holder.bridge;
    }

    static void set(ExtendedDataBridge bridge) {
        Holder.bridge = bridge;
    }

    final class Holder {
        private static volatile ExtendedDataBridge bridge;

        private Holder() {
        }
    }
}
