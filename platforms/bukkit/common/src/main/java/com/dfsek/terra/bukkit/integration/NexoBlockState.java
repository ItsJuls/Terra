package com.dfsek.terra.bukkit.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;


/**
 * A Nexo custom block. Nexo blocks are plain block states (note block, tripwire or chorus), so once Nexo has loaded its blocks
 * they are written directly during generation like any other block. Until then (Nexo enables after the worlds load), they are
 * queued and placed through Nexo when the chunk loads.
 */
public final class NexoBlockState extends PlacementBlockState {
    private final String id;
    private volatile BlockData resolved;

    NexoBlockState(String id) {
        super(new Placement.NexoBlock(id), "nexo:" + id);
        this.id = id;
    }

    @Override
    public BlockData immediate() {
        BlockData data = resolved;
        if(data == null && Bukkit.getPluginManager().isPluginEnabled(NexoHook.PLUGIN)) {
            data = NexoHook.blockData(id);
            if(data != null) resolved = data;
        }
        return data;
    }
}
