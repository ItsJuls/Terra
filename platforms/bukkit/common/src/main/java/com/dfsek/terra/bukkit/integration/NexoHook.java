package com.dfsek.terra.bukkit.integration;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.api.NexoFurniture;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;


/**
 * Calls into Nexo. Only loaded once Nexo is enabled.
 */
final class NexoHook {
    static final String PLUGIN = "Nexo";

    private NexoHook() {
    }

    /**
     * The block state behind a Nexo custom block (a note block, tripwire or chorus state), or null if Nexo doesn't know it (yet).
     */
    static BlockData blockData(String id) {
        return NexoBlocks.blockData(id);
    }

    static boolean placeBlock(String id, Location location) {
        if(!NexoBlocks.isCustomBlock(id)) return false;
        NexoBlocks.place(id, location);
        return true;
    }

    static boolean placeFurniture(String id, Location location, float yaw, BlockFace face) {
        ItemDisplay display = NexoFurniture.place(id, location, yaw, face);
        return display != null;
    }
}
