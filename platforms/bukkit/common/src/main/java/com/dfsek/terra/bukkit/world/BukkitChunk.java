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

import org.jetbrains.annotations.NotNull;

import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.world.ServerWorld;
import com.dfsek.terra.api.world.chunk.Chunk;
import com.dfsek.terra.bukkit.world.block.data.BukkitBlockStateExtended;


public class BukkitChunk implements Chunk {
    private final org.bukkit.Chunk delegate;

    public BukkitChunk(org.bukkit.Chunk delegate) {
        this.delegate = delegate;
    }

    @Override
    public org.bukkit.Chunk getHandle() {
        return delegate;
    }

    @Override
    public void setBlock(int x, int y, int z, BlockState data, boolean physics) {
        delegate.getBlock(x, y, z).setBlockData(BukkitAdapter.adapt(data), physics);
        loadBlockEntity(x, y, z, data);
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull BlockState blockState) {
        delegate.getBlock(x, y, z).setBlockData(BukkitAdapter.adapt(blockState));
        loadBlockEntity(x, y, z, blockState);
    }

    @Override
    public @NotNull BlockState getBlock(int x, int y, int z) {
        return BukkitAdapter.adapt(delegate.getBlock(x, y, z).getBlockData());
    }

    @Override
    public int getX() {
        return delegate.getX();
    }

    @Override
    public int getZ() {
        return delegate.getZ();
    }

    @Override
    public ServerWorld getWorld() {
        return BukkitAdapter.adapt(delegate.getWorld());
    }

    private void loadBlockEntity(int x, int y, int z, BlockState data) {
        if(data instanceof BukkitBlockStateExtended extended && ExtendedDataBridge.get() != null) {
            ExtendedDataBridge.get().loadBlockEntity(delegate.getWorld(), (delegate.getX() << 4) + x, y, (delegate.getZ() << 4) + z,
                extended.getData().handle());
        }
    }
}
