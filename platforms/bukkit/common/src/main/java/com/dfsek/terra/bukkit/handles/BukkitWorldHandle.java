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

package com.dfsek.terra.bukkit.handles;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.entity.EntityType;
import com.dfsek.terra.api.handle.WorldHandle;
import com.dfsek.terra.bukkit.integration.PluginBlocks;
import com.dfsek.terra.bukkit.util.BukkitUtils;
import com.dfsek.terra.bukkit.world.BukkitExtendedData;
import com.dfsek.terra.bukkit.world.ExtendedDataBridge;
import com.dfsek.terra.bukkit.world.block.data.BukkitBlockState;
import com.dfsek.terra.bukkit.world.block.data.BukkitBlockStateExtended;
import com.dfsek.terra.bukkit.world.entity.BukkitEntityType;
import com.dfsek.terra.bukkit.world.entity.BukkitEntityTypeExtended;


public class BukkitWorldHandle implements WorldHandle {
    private static final Logger logger = LoggerFactory.getLogger(BukkitWorldHandle.class);
    private final BlockState air;

    public BukkitWorldHandle() {
        this.air = BukkitBlockState.newInstance(Material.AIR.createBlockData());
    }

    @Override
    public synchronized @NotNull BlockState createBlockState(@NotNull String data) {
        BlockState pluginBlock = PluginBlocks.parse(data);
        if(pluginBlock != null) return pluginBlock;

        int nbtStart = data.indexOf('{');
        String blockData = nbtStart == -1 ? data : data.substring(0, nbtStart);
        org.bukkit.block.data.BlockData bukkitData = Bukkit.createBlockData(
            blockData); // somehow bukkit managed to make this not thread safe! :)
        if(nbtStart == -1) {
            return BukkitBlockState.newInstance(bukkitData);
        }

        String snbt = data.substring(nbtStart);
        ExtendedDataBridge bridge = ExtendedDataBridge.get();
        if(bridge == null) {
            logger.warn("Block entity data is not supported without NMS bindings, ignoring {} of {}", snbt, blockData);
            return BukkitBlockState.newInstance(bukkitData);
        }
        Object blockEntityData = bridge.prepareBlockEntityData(bukkitData, bridge.parseCompound(snbt));
        if(blockEntityData == null) { // block has no block entity, like on other platforms the data is ignored.
            return BukkitBlockState.newInstance(bukkitData);
        }
        return new BukkitBlockStateExtended(bukkitData, new BukkitExtendedData(snbt, blockEntityData));
    }

    @Override
    public @NotNull BlockState air() {
        return air;
    }

    @Override
    public @NotNull EntityType getEntity(@NotNull String id) {
        int nbtStart = id.indexOf('{');
        if(nbtStart == -1) {
            return BukkitUtils.getEntityType(id);
        }

        BukkitEntityType type = BukkitUtils.getEntityType(id.substring(0, nbtStart));
        String snbt = id.substring(nbtStart);
        ExtendedDataBridge bridge = ExtendedDataBridge.get();
        if(bridge == null) {
            logger.warn("Entity data is not supported without NMS bindings, ignoring {} of {}", snbt, type.getHandle());
            return type;
        }
        return new BukkitEntityTypeExtended(type.getHandle(),
            new BukkitExtendedData(snbt, bridge.prepareEntityData(type.getHandle(), bridge.parseCompound(snbt))));
    }
}
