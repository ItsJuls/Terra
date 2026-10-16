package com.dfsek.terra.bukkit.integration;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.dfsek.terra.bukkit.world.block.data.BukkitBlockState;


/**
 * A "block" that is really something another plugin places: Nexo furniture, a CustomCrops crop, or a Nexo block that can't be
 * resolved yet. Writes clear the position and queue the placement (see {@link PendingPlacements}).
 */
public class PlacementBlockState extends BukkitBlockState {
    private static final BlockData AIR = Material.AIR.createBlockData();

    private final Placement placement;
    private final String asString;

    PlacementBlockState(Placement placement, String asString) {
        super(AIR);
        this.placement = placement;
        this.asString = asString;
    }

    public Placement placement() {
        return placement;
    }

    /**
     * Block data that can be written directly, or null if the placement has to wait for a loaded world.
     */
    public BlockData immediate() {
        return null;
    }

    @Override
    public BlockData getHandle() {
        BlockData data = immediate();
        return data == null ? AIR : data;
    }

    @Override
    public String getAsString(boolean properties) {
        BlockData data = immediate();
        return data == null ? asString : data.getAsString(!properties);
    }
}
