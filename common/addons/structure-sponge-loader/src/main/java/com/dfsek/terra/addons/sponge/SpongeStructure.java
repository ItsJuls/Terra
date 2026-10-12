/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.sponge;

import com.dfsek.seismic.type.Rotation;
import com.dfsek.seismic.type.vector.Vector2Int;
import com.dfsek.seismic.type.vector.Vector3Int;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.entity.EntityType;
import com.dfsek.terra.api.registry.key.Keyed;
import com.dfsek.terra.api.registry.key.RegistryKey;
import com.dfsek.terra.api.structure.Structure;
import com.dfsek.terra.api.world.WritableWorld;


public class SpongeStructure implements Structure, Keyed<SpongeStructure> {
    private final int width, height, length;

    /**
     * Palette index of each block, {@code x + z * width + y * width * length}. Negative entries are skipped (structure void).
     */
    private final short[] blocks;

    /**
     * Block states by rotation ({@link Rotation#ordinal()}), then palette index. Null entries are skipped.
     */
    private final BlockState[][] palettes;

    /**
     * Blocks with block entity data, by index into {@link #blocks}, as states by rotation.
     */
    private final Map<Integer, BlockState[]> blockEntities;

    private final List<SchematicEntity> entities;

    private final int offsetX, offsetY, offsetZ;

    private final RegistryKey id;

    public SpongeStructure(int width, int height, int length, short[] blocks, BlockState[][] palettes,
                           Map<Integer, BlockState[]> blockEntities, List<SchematicEntity> entities, Vector3Int offset,
                           RegistryKey id) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.palettes = palettes;
        this.blockEntities = blockEntities;
        this.entities = entities;
        this.offsetX = offset.getX();
        this.offsetY = offset.getY();
        this.offsetZ = offset.getZ();
        this.id = id;
    }

    @Override
    public boolean generate(Vector3Int location, WritableWorld world, RandomGenerator random, Rotation rotation) {
        int bX = location.getX();
        int bY = location.getY();
        int bZ = location.getZ();
        BlockState[] palette = palettes[rotation.ordinal()];
        for(int x = 0; x < width; x++) {
            for(int z = 0; z < length; z++) {
                Vector2Int r = Vector2Int.Mutable.of(x + offsetX, z + offsetZ).rotate(rotation);
                int rX = r.getX();
                int rZ = r.getZ();
                for(int y = 0; y < height; y++) {
                    int index = x + z * width + y * width * length;
                    short paletteIndex = blocks[index];
                    if(paletteIndex < 0) continue;
                    BlockState[] blockEntity = blockEntities.get(index);
                    BlockState state = blockEntity != null ? blockEntity[rotation.ordinal()] : palette[paletteIndex];
                    if(state == null) continue;
                    world.setBlockState(bX + rX, bY + y + offsetY, bZ + rZ, state);
                }
            }
        }
        for(SchematicEntity entity : entities) {
            double x = entity.x() + offsetX;
            double z = entity.z() + offsetZ;
            // Blocks rotate by their corner: the block at x lands at -x, covering [-x, 1 - x). An entity inside it has to land
            // inside it too, so negated coordinates are mirrored within the block (1 - x rather than -x).
            double rX = switch(rotation) {
                case NONE -> x;
                case CW_90 -> z;
                case CW_180 -> 1 - x;
                case CCW_90 -> 1 - z;
            };
            double rZ = switch(rotation) {
                case NONE -> z;
                case CW_90 -> 1 - x;
                case CW_180 -> 1 - z;
                case CCW_90 -> x;
            };
            world.spawnEntity(bX + rX, bY + entity.y() + offsetY, bZ + rZ, entity.type());
        }
        return true;
    }

    @Override
    public RegistryKey getRegistryKey() {
        return id;
    }

    /**
     * An entity saved in a schematic, positioned relative to the schematic's minimum corner.
     */
    public record SchematicEntity(double x, double y, double z, EntityType type) {
    }
}
