package com.dfsek.terra.bukkit.integration;

import net.momirealms.customcrops.api.BukkitCustomCropsAPI;
import net.momirealms.customcrops.api.CustomCropsAPI;
import org.bukkit.Location;


/**
 * Calls into CustomCrops. Only loaded once CustomCrops is enabled.
 */
final class CustomCropsHook {
    static final String PLUGIN = "CustomCrops";

    private CustomCropsHook() {
    }

    /**
     * Place a crop through CustomCrops, so it is registered and grows. Fails if the crop isn't configured or CustomCrops doesn't
     * manage the world.
     */
    static boolean placeCrop(String id, Location location, int point) {
        CustomCropsAPI api = BukkitCustomCropsAPI.get();
        return api != null && api.placeCrop(location, id, point);
    }
}
