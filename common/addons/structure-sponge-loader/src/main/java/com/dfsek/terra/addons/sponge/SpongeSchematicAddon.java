/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.sponge;

import com.dfsek.seismic.type.Rotation;
import com.dfsek.seismic.type.vector.Vector3Int;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.SNBTUtil;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.NumberTag;
import net.querz.nbt.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.addons.sponge.SpongeStructure.SchematicEntity;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.block.state.BlockState;
import com.dfsek.terra.api.event.events.config.pack.ConfigPackPreLoadEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.inject.annotations.Inject;
import com.dfsek.terra.api.registry.CheckedRegistry;
import com.dfsek.terra.api.structure.Structure;
import com.dfsek.terra.api.util.FileUtil;


/**
 * Loads Sponge schematics ({@code .schem}, as saved by WorldEdit) as structures, versions 1 to 3.
 */
public class SpongeSchematicAddon implements AddonInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpongeSchematicAddon.class);

    // Saved alongside block entity and entity data, but placing the schematic decides these.
    private static final Set<String> POSITION_KEYS = Set.of("Pos", "Id", "id", "x", "y", "z");
    private static final Set<String> ENTITY_SKIPPED_KEYS = Set.of("Pos", "Id", "id", "UUID");

    @Inject
    private Platform platform;

    @Inject
    private BaseAddon addon;

    private static InputStream detectDecompression(InputStream is) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(is, 2);
        int signature = (pbis.read() & 0xFF) + (pbis.read() << 8);
        pbis.unread(signature >> 8);
        pbis.unread(signature & 0xFF);
        if(signature == GZIPInputStream.GZIP_MAGIC) {
            return new GZIPInputStream(pbis);
        }
        return pbis;
    }

    /**
     * Block data is a sequence of varints, one palette index per block.
     */
    private static short[] readVarInts(byte[] data, int count, String id) {
        short[] values = new short[count];
        int position = 0;
        for(int i = 0; i < count; i++) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if(position >= data.length) {
                    throw new IllegalArgumentException("Failed to parse Sponge schematic " + id + ": block data ends early");
                }
                b = data[position++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while((b & 0x80) != 0);
            if(value > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Failed to parse Sponge schematic " + id + ": too many block types");
            }
            values[i] = (short) value;
        }
        return values;
    }

    private static ListTag<CompoundTag> compoundList(CompoundTag tag, String... keys) {
        for(String key : keys) {
            ListTag<?> list = tag.getListTag(key);
            if(list != null && list.size() > 0) return list.asCompoundTagList();
        }
        return null;
    }

    private static String toSNBT(CompoundTag tag, Set<String> skip) throws IOException {
        CompoundTag copy = tag.clone();
        skip.forEach(copy::remove);
        return copy.size() == 0 ? "" : SNBTUtil.toSNBT(copy);
    }

    @Override
    public void initialize() {
        platform.getEventManager()
            .getHandler(FunctionalEventHandler.class)
            .register(addon, ConfigPackPreLoadEvent.class)
            .then(event -> {
                CheckedRegistry<Structure> structureRegistry = event.getPack().getOrCreateRegistry(Structure.class);
                try {
                    FileUtil.filesWithExtension(event.getPack().getRootPath(), ".schem")
                        .entrySet()
                        .stream()
                        .map(entry -> {
                            try(InputStream in = Files.newInputStream(entry.getValue())) {
                                return convert(in, FileUtil.fileName(entry.getKey()));
                            } catch(IOException e) {
                                throw new RuntimeException("Failed to load config " + entry.getKey(), e);
                            }
                        })
                        .forEach(structureRegistry::register);
                } catch(IOException e) {
                    throw new RuntimeException("Error occurred while reading config pack files", e);
                }
            })
            .failThrough();
    }

    public SpongeStructure convert(InputStream in, String id) {
        try {
            CompoundTag root = (CompoundTag) new NBTDeserializer(false).fromStream(detectDecompression(in)).getTag();
            // Version 3 nests everything in a "Schematic" compound.
            CompoundTag schematic = root.containsKey("Schematic") ? root.getCompoundTag("Schematic") : root;
            int version = schematic.getInt("Version");
            int width = schematic.getShort("Width") & 0xFFFF;
            int height = schematic.getShort("Height") & 0xFFFF;
            int length = schematic.getShort("Length") & 0xFFFF;

            CompoundTag palette;
            byte[] blockData;
            ListTag<CompoundTag> blockEntities;
            Vector3Int offset;
            // Entity positions in versions 1 and 2 are world positions; this is the world position of the minimum corner.
            int[] entityOrigin = { 0, 0, 0 };
            switch(version) {
                case 1, 2 -> {
                    palette = schematic.getCompoundTag("Palette");
                    blockData = schematic.getByteArray("BlockData");
                    blockEntities = compoundList(schematic, "BlockEntities", "TileEntities");
                    offset = worldEditOffset(schematic.getCompoundTag("Metadata"));
                    if(schematic.containsKey("Offset")) entityOrigin = schematic.getIntArray("Offset");
                }
                case 3 -> {
                    CompoundTag blocks = schematic.getCompoundTag("Blocks");
                    if(blocks == null) throw new IllegalArgumentException("Failed to parse Sponge schematic " + id + ": no blocks");
                    palette = blocks.getCompoundTag("Palette");
                    blockData = blocks.getByteArray("Data");
                    blockEntities = compoundList(blocks, "BlockEntities");
                    int[] offsetArray = schematic.containsKey("Offset") ? schematic.getIntArray("Offset") : new int[3];
                    offset = Vector3Int.of(offsetArray[0], offsetArray[1], offsetArray[2]);
                }
                default -> throw new IllegalArgumentException(
                    "Failed to parse Sponge schematic " + id + ": Unsupported format version: " + version);
            }

            String[] paletteStrings = new String[palette.size()];
            for(Map.Entry<String, Tag<?>> entry : palette.entrySet()) {
                int index = ((IntTag) entry.getValue()).asInt();
                if(index < 0 || index >= paletteStrings.length) {
                    throw new IllegalArgumentException("Failed to parse Sponge schematic " + id + ": bad palette index " + index);
                }
                paletteStrings[index] = entry.getKey();
            }

            short[] blocks = readVarInts(blockData, width * height * length, id);
            for(int i = 0; i < blocks.length; i++) {
                String block = blocks[i] < paletteStrings.length ? paletteStrings[blocks[i]] : null;
                if(block == null || block.startsWith("minecraft:structure_void")) blocks[i] = -1;
            }

            Rotation[] rotations = Rotation.values();
            BlockState[][] palettes = new BlockState[rotations.length][paletteStrings.length];
            for(Rotation rotation : rotations) {
                for(int i = 0; i < paletteStrings.length; i++) {
                    if(paletteStrings[i] == null) continue;
                    palettes[rotation.ordinal()][i] = createBlockState(BlockStateRotation.rotate(paletteStrings[i], rotation), id);
                }
            }

            Map<Integer, BlockState[]> blockEntityStates = new HashMap<>();
            if(blockEntities != null) {
                for(CompoundTag blockEntity : blockEntities) {
                    int[] pos = blockEntity.getIntArray("Pos");
                    if(pos == null || pos.length != 3 || pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= width ||
                       pos[1] >= height || pos[2] >= length) continue;
                    int index = pos[0] + pos[2] * width + pos[1] * width * length;
                    if(blocks[index] < 0) continue;
                    // Version 3 keeps the data in "Data", older versions next to Pos and Id.
                    CompoundTag data = blockEntity.containsKey("Data") ? blockEntity.getCompoundTag("Data") : blockEntity;
                    String nbt = toSNBT(data, POSITION_KEYS);
                    if(nbt.isEmpty()) continue;
                    BlockState[] states = new BlockState[rotations.length];
                    for(Rotation rotation : rotations) {
                        String block = BlockStateRotation.rotate(paletteStrings[blocks[index]], rotation);
                        BlockState state = createBlockState(block + nbt, id);
                        states[rotation.ordinal()] = state != null ? state : palettes[rotation.ordinal()][blocks[index]];
                    }
                    blockEntityStates.put(index, states);
                }
            }

            List<SchematicEntity> entities = new ArrayList<>();
            ListTag<CompoundTag> entityList = compoundList(schematic, "Entities");
            if(entityList != null) {
                for(CompoundTag entity : entityList) {
                    SchematicEntity loaded = loadEntity(entity, entityOrigin, id);
                    if(loaded != null) entities.add(loaded);
                }
            }

            return new SpongeStructure(width, height, length, blocks, palettes, blockEntityStates, List.copyOf(entities), offset,
                addon.key(id));
        } catch(IOException e) {
            throw new IllegalArgumentException("Failed to parse Sponge schematic " + id, e);
        }
    }

    private Vector3Int worldEditOffset(CompoundTag metadata) {
        if(metadata == null) return Vector3Int.zero();
        // Use WorldEdit defined legacy relative offset if it exists in schematic metadata
        IntTag worldEditOffsetX = metadata.getIntTag("WEOffsetX");
        IntTag worldEditOffsetY = metadata.getIntTag("WEOffsetY");
        IntTag worldEditOffsetZ = metadata.getIntTag("WEOffsetZ");
        if(worldEditOffsetX != null || worldEditOffsetY != null || worldEditOffsetZ != null) {
            if(worldEditOffsetX == null || worldEditOffsetY == null || worldEditOffsetZ == null) {
                throw new IllegalArgumentException("Failed to parse Sponge schematic: Malformed WorldEdit offset");
            }
            return Vector3Int.of(worldEditOffsetX.asInt(), worldEditOffsetY.asInt(), worldEditOffsetZ.asInt());
        }
        // Relative offset handling via 'Offset' field is ambiguous in spec 2 so just apply no offset
        return Vector3Int.zero();
    }

    private SchematicEntity loadEntity(CompoundTag entity, int[] origin, String schematic) throws IOException {
        String type = entity.getString("Id");
        ListTag<?> pos = entity.getListTag("Pos");
        if(type == null || type.isEmpty() || pos == null || pos.size() != 3) return null;
        double[] position = new double[3];
        for(int i = 0; i < 3; i++) {
            position[i] = ((NumberTag<?>) pos.get(i)).asDouble() - origin[i];
        }
        CompoundTag data = entity.containsKey("Data") ? entity.getCompoundTag("Data") : entity;
        try {
            return new SchematicEntity(position[0], position[1], position[2],
                platform.getWorldHandle().getEntity(type + toSNBT(data, ENTITY_SKIPPED_KEYS)));
        } catch(RuntimeException e) {
            LOGGER.warn("Skipping entity {} at {} in schematic {}: {}", type, Arrays.toString(position), schematic, e.getMessage());
            return null;
        }
    }

    private BlockState createBlockState(String block, String schematic) {
        try {
            return platform.getWorldHandle().createBlockState(block);
        } catch(RuntimeException e) {
            LOGGER.warn("Skipping block {} in schematic {}: {}", block, schematic, e.getMessage());
            return null;
        }
    }
}
