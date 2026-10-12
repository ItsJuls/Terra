package com.dfsek.terra.mod.util;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.mod.config.SpawnSettings;
import com.dfsek.terra.mod.config.VanillaBiomeProperties;
import com.dfsek.terra.mod.mixin.access.BiomeAccessor;


public class BiomeUtil {
    public static final Map<Identifier, List<Identifier>>
        TERRA_BIOME_MAP = new HashMap<>();

    public static Biome createBiome(Biome vanilla, VanillaBiomeProperties vanillaBiomeProperties) {
        BiomeSpecialEffects vanillaEffects = vanilla.getSpecialEffects();
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder();

        effects.waterColor(Objects.requireNonNullElse(vanillaBiomeProperties.getWaterColor(), vanilla.getWaterColor()))
            .grassColorModifier(
                Objects.requireNonNullElse(vanillaBiomeProperties.getGrassColorModifier(), vanillaEffects.grassColorModifier()));

        if(vanillaBiomeProperties.getGrassColor() == null) {
            vanillaEffects.grassColorOverride().ifPresent(effects::grassColorOverride);
        } else {
            effects.grassColorOverride(vanillaBiomeProperties.getGrassColor());
        }

        if(vanillaBiomeProperties.getFoliageColor() == null) {
            vanillaEffects.foliageColorOverride().ifPresent(effects::foliageColorOverride);
        } else {
            effects.foliageColorOverride(vanillaBiomeProperties.getFoliageColor());
        }

        if(vanillaBiomeProperties.getDryFoliageColor() == null) {
            vanillaEffects.dryFoliageColorOverride().ifPresent(effects::dryFoliageColorOverride);
        } else {
            effects.dryFoliageColorOverride(vanillaBiomeProperties.getDryFoliageColor());
        }

        // Since 1.21.11, colors, particles, sounds, music and mob spawns are environment attributes.
        // Start from the vanilla biome's attributes and override the ones configured in the pack.
        BiomeBuilder builder = new BiomeBuilder().putAttributes(vanilla.getAttributes());

        if(vanillaBiomeProperties.getFogColor() != null) {
            builder.setAttribute(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(vanillaBiomeProperties.getFogColor()));
        }

        if(vanillaBiomeProperties.getWaterFogColor() != null) {
            builder.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, ARGB.vector3fFromRGB24(vanillaBiomeProperties.getWaterFogColor()));
        }

        if(vanillaBiomeProperties.getSkyColor() != null) {
            builder.setAttribute(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(vanillaBiomeProperties.getSkyColor()));
        }

        if(vanillaBiomeProperties.getParticleConfig() != null) {
            builder.setAttribute(EnvironmentAttributes.AMBIENT_PARTICLES, List.of(vanillaBiomeProperties.getParticleConfig()));
        }

        if(vanillaBiomeProperties.getLoopSound() != null || vanillaBiomeProperties.getMoodSound() != null ||
           vanillaBiomeProperties.getAdditionsSound() != null) {
            AmbientSounds vanillaSounds = vanilla.getAttributes().applyModifier(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.EMPTY);
            builder.setAttribute(EnvironmentAttributes.AMBIENT_SOUNDS, new AmbientSounds(
                vanillaBiomeProperties.getLoopSound() == null
                ? vanillaSounds.loop()
                : Optional.of(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(vanillaBiomeProperties.getLoopSound())),
                vanillaBiomeProperties.getMoodSound() == null
                ? vanillaSounds.mood()
                : Optional.of(vanillaBiomeProperties.getMoodSound()),
                vanillaBiomeProperties.getAdditionsSound() == null
                ? vanillaSounds.additions()
                : List.of(vanillaBiomeProperties.getAdditionsSound())));
        }

        if(vanillaBiomeProperties.getMusic() != null) {
            builder.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(vanillaBiomeProperties.getMusic()));
        }

        if(vanillaBiomeProperties.getMusicVolume() != null) {
            builder.setAttribute(EnvironmentAttributes.MUSIC_VOLUME, vanillaBiomeProperties.getMusicVolume());
        }

        SpawnSettings spawnSettings = vanillaBiomeProperties.getSpawnSettings();
        if(spawnSettings != null) {
            builder.setAttribute(EnvironmentAttributes.NATURAL_MOB_SPAWNS, spawnSettings.mobSpawnSettings());
            if(spawnSettings.creatureSpawnProbability() != null) {
                builder.setAttribute(EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY, spawnSettings.creatureSpawnProbability());
            }
        }

        builder.hasPrecipitation(Objects.requireNonNullElse(vanillaBiomeProperties.getPrecipitation(), vanilla.hasPrecipitation()));

        builder.temperature(Objects.requireNonNullElse(vanillaBiomeProperties.getTemperature(), vanilla.getBaseTemperature()));

        builder.downfall(Objects.requireNonNullElse(vanillaBiomeProperties.getDownfall(),
            ((BiomeAccessor) ((Object) vanilla)).getWeather().downfall()));

        builder.temperatureAdjustment(Objects.requireNonNullElse(vanillaBiomeProperties.getTemperatureModifier(),
            ((BiomeAccessor) ((Object) vanilla)).getWeather().temperatureModifier()));

        return builder
            .specialEffects(effects.build())
            .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
            .build();
    }

    public static String createBiomeID(ConfigPack pack, com.dfsek.terra.api.registry.key.RegistryKey biomeID) {
        return pack.getID()
                   .toLowerCase() + "/" + biomeID.getNamespace().toLowerCase(Locale.ROOT) + "/" + biomeID.getID().toLowerCase(Locale.ROOT);
    }

    public static Map<Identifier, List<Identifier>> getTerraBiomeMap() {
        return Map.copyOf(TERRA_BIOME_MAP);
    }
}
