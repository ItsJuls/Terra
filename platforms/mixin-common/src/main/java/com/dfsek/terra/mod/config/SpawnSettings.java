package com.dfsek.terra.mod.config;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.biome.MobSpawnSettings;


/**
 * Biome spawn settings. Since 1.21.11 the creature spawn probability is a separate biome attribute rather than part of
 * {@link MobSpawnSettings}.
 */
public record SpawnSettings(MobSpawnSettings mobSpawnSettings, @Nullable Float creatureSpawnProbability) {
}
