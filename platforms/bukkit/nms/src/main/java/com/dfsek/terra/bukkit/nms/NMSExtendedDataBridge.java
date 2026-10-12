package com.dfsek.terra.bukkit.nms;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.bukkit.RegionAccessor;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftRegionAccessor;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dfsek.terra.bukkit.world.ExtendedDataBridge;


/**
 * Block entity and entity NBT support, mirroring the Fabric implementation (MinecraftWorldHandle / MinecraftUtil).
 */
public class NMSExtendedDataBridge implements ExtendedDataBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NMSExtendedDataBridge.class);

    @Override
    public Object parseCompound(String snbt) {
        try {
            StringReader reader = new StringReader(snbt);
            CompoundTag tag = TagParser.parseCompoundAsArgument(reader);
            if(reader.canRead()) { // Fabric ignores trailing data, so packs may contain it.
                LOGGER.warn("Ignoring trailing data \"{}\" after NBT \"{}\"", reader.getRemaining(), snbt);
            }
            return tag;
        } catch(CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid NBT \"" + snbt + "\": " + e.getMessage(), e);
        }
    }

    @Override
    public @Nullable Object prepareBlockEntityData(BlockData block, Object compound) {
        BlockState state = ((CraftBlockData) block).getState();
        if(!state.hasBlockEntity()) return null;

        BlockEntity blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
        if(blockEntity == null) return null;

        CompoundTag nbt = ((CompoundTag) compound).copy();
        nbt.putInt("x", 0);
        nbt.putInt("y", 0);
        nbt.putInt("z", 0);
        nbt.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString());
        return nbt;
    }

    @Override
    public Object prepareEntityData(EntityType type, Object compound) {
        CompoundTag nbt = ((CompoundTag) compound).copy();
        nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(CraftEntityType.bukkitToMinecraft(type)).toString());
        return nbt;
    }

    @Override
    public void loadBlockEntity(RegionAccessor world, int x, int y, int z, Object blockEntityData) {
        WorldGenLevel level = ((CraftRegionAccessor) world).getHandle();
        BlockPos pos = new BlockPos(x, y, z);
        loadBlockEntity(level.getChunk(pos), pos, level.registryAccess(), (CompoundTag) blockEntityData);
        if(level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(pos);
        }
    }

    @Override
    public void loadBlockEntity(ChunkData chunk, int x, int y, int z, Object blockEntityData) {
        ChunkAccess access = ((CraftChunkData) chunk).getHandle();
        BlockPos pos = new BlockPos((access.getPos().x() << 4) + x, y, (access.getPos().z() << 4) + z);
        loadBlockEntity(access, pos, MinecraftServer.getServer().registryAccess(), (CompoundTag) blockEntityData);
    }

    //[Vanilla Copy] see MinecraftUtil#loadBlockEntity on Fabric
    private static void loadBlockEntity(ChunkAccess chunk, BlockPos pos, HolderLookup.Provider registries, CompoundTag nbt) {
        BlockState state = chunk.getBlockState(pos);
        BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, nbt.copy(), registries);
        if(blockEntity != null) {
            // A level chunk sets the level itself; block entities of chunks being generated must not reference the level.
            chunk.setBlockEntity(blockEntity);
        }
    }

    @Override
    public org.bukkit.entity.@Nullable Entity spawnEntity(RegionAccessor world, double x, double y, double z, Object entityData) {
        CraftRegionAccessor accessor = (CraftRegionAccessor) world;
        Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive((CompoundTag) ((CompoundTag) entityData).copy(),
            accessor.getHandle().getLevel(), new EntitySpawnRequest(EntitySpawnReason.CHUNK_GENERATION, false), loaded -> {
                loaded.snapTo(x, y, z, loaded.getYRot(), loaded.getXRot());
                return loaded;
            });
        if(entity == null) return null;
        accessor.addEntityWithPassengers(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        return entity.getBukkitEntity();
    }
}
