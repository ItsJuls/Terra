package com.dfsek.terra.bukkit.nms.config;

import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jetbrains.annotations.Nullable;


/**
 * Biome spawn settings. Since 1.21.11 the creature spawn probability is a separate biome attribute rather than part of
 * {@link MobSpawnSettings}.
 */
public record SpawnSettings(MobSpawnSettings mobSpawnSettings, @Nullable Float creatureSpawnProbability) {
}
