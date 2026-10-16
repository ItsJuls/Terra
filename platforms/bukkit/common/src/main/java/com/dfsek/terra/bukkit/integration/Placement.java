package com.dfsek.terra.bukkit.integration;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;


/**
 * Something from another plugin that has to be placed in a loaded world: it can't be written while the chunk is generating.
 * Implementations only call into their plugin from {@link #place}, so Terra loads without those plugins installed.
 */
public sealed interface Placement {
    /**
     * Name of the plugin this placement needs.
     */
    String plugin();

    /**
     * Place this at a location in a loaded world, on the thread that owns it.
     *
     * @return whether it was placed. False means the plugin couldn't place it yet (e.g. its content hasn't loaded).
     */
    boolean place(Location location);

    /**
     * Serialized form, read back by {@link #parse}.
     */
    String serialize();

    static Placement parse(String serialized) {
        String[] parts = serialized.split("\t");
        return switch(parts[0]) {
            case "nexo_block" -> new NexoBlock(parts[1]);
            case "nexo_furniture" -> new NexoFurniture(parts[1], Float.parseFloat(parts[2]), BlockFace.valueOf(parts[3]));
            case "customcrops_crop" -> new CustomCrop(parts[1], Integer.parseInt(parts[2]));
            default -> throw new IllegalArgumentException("Unknown placement " + parts[0]);
        };
    }

    record NexoBlock(String id) implements Placement {
        @Override
        public String plugin() {
            return NexoHook.PLUGIN;
        }

        @Override
        public boolean place(Location location) {
            return NexoHook.placeBlock(id, location);
        }

        @Override
        public String serialize() {
            return "nexo_block\t" + id;
        }
    }

    record NexoFurniture(String id, float yaw, BlockFace face) implements Placement {
        @Override
        public String plugin() {
            return NexoHook.PLUGIN;
        }

        @Override
        public boolean place(Location location) {
            return NexoHook.placeFurniture(id, location, yaw, face);
        }

        @Override
        public String serialize() {
            return "nexo_furniture\t" + id + "\t" + yaw + "\t" + face.name();
        }
    }

    record CustomCrop(String id, int point) implements Placement {
        @Override
        public String plugin() {
            return CustomCropsHook.PLUGIN;
        }

        @Override
        public boolean place(Location location) {
            return CustomCropsHook.placeCrop(id, location, point);
        }

        @Override
        public String serialize() {
            return "customcrops_crop\t" + id + "\t" + point;
        }
    }
}
