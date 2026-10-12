package com.dfsek.terra.mod.util;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeBuilder;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.mod.config.VanillaBiomeProperties;
import com.dfsek.terra.mod.mixin.access.BiomeAccessor;


public class BiomeUtil {
    public static final Map<ResourceLocation, List<ResourceLocation>>
        TERRA_BIOME_MAP = new HashMap<>();

    public static Biome createBiome(Biome vanilla, VanillaBiomeProperties vanillaBiomeProperties) {
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder();

        net.minecraft.world.level.biome.Biome.BiomeBuilder builder = new BiomeBuilder();

        effects.waterColor(Objects.requireNonNullElse(vanillaBiomeProperties.getWaterColor(), vanilla.getWaterColor()))
            .waterFogColor(Objects.requireNonNullElse(vanillaBiomeProperties.getWaterFogColor(), vanilla.getWaterFogColor()))
            .fogColor(Objects.requireNonNullElse(vanillaBiomeProperties.getFogColor(), vanilla.getFogColor()))
            .skyColor(Objects.requireNonNullElse(vanillaBiomeProperties.getSkyColor(), vanilla.getSkyColor()))
            .grassColorModifier(
                Objects.requireNonNullElse(vanillaBiomeProperties.getGrassColorModifier(), vanilla.getSpecialEffects().getGrassColorModifier()))
            .backgroundMusicVolume(Objects.requireNonNullElse(vanillaBiomeProperties.getMusicVolume(), vanilla.getBackgroundMusicVolume()));

        if(vanillaBiomeProperties.getGrassColor() == null) {
            vanilla.getSpecialEffects().getGrassColorOverride().ifPresent(effects::grassColorOverride);
        } else {
            effects.grassColorOverride(vanillaBiomeProperties.getGrassColor());
        }

        if(vanillaBiomeProperties.getFoliageColor() == null) {
            vanilla.getSpecialEffects().getFoliageColorOverride().ifPresent(effects::foliageColorOverride);
        } else {
            effects.foliageColorOverride(vanillaBiomeProperties.getFoliageColor());
        }

        if(vanillaBiomeProperties.getDryFoliageColor() == null) {
            vanilla.getSpecialEffects().getDryFoliageColorOverride().ifPresent(effects::dryFoliageColorOverride);
        } else {
            effects.dryFoliageColorOverride(vanillaBiomeProperties.getDryFoliageColor());
        }

        if(vanillaBiomeProperties.getParticleConfig() == null) {
            vanilla.getSpecialEffects().getAmbientParticleSettings().ifPresent(effects::ambientParticle);
        } else {
            effects.ambientParticle(vanillaBiomeProperties.getParticleConfig());
        }

        if(vanillaBiomeProperties.getLoopSound() == null) {
            vanilla.getSpecialEffects().getAmbientLoopSoundEvent().ifPresent(effects::ambientLoopSound);
        } else {
            effects.ambientLoopSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(vanillaBiomeProperties.getLoopSound()));
        }

        if(vanillaBiomeProperties.getMoodSound() == null) {
            vanilla.getSpecialEffects().getAmbientMoodSettings().ifPresent(effects::ambientMoodSound);
        } else {
            effects.ambientMoodSound(vanillaBiomeProperties.getMoodSound());
        }

        if(vanillaBiomeProperties.getAdditionsSound() == null) {
            vanilla.getSpecialEffects().getAmbientAdditionsSettings().ifPresent(effects::ambientAdditionsSound);
        } else {
            effects.ambientAdditionsSound(vanillaBiomeProperties.getAdditionsSound());
        }

        if(vanillaBiomeProperties.getMusic() == null) {
            vanilla.getSpecialEffects().getBackgroundMusic().ifPresent(effects::backgroundMusic);
        } else {
            effects.backgroundMusic(vanillaBiomeProperties.getMusic());
        }

        builder.hasPrecipitation(Objects.requireNonNullElse(vanillaBiomeProperties.getPrecipitation(), vanilla.hasPrecipitation()));

        builder.temperature(Objects.requireNonNullElse(vanillaBiomeProperties.getTemperature(), vanilla.getBaseTemperature()));

        builder.downfall(Objects.requireNonNullElse(vanillaBiomeProperties.getDownfall(),
            ((BiomeAccessor) ((Object) vanilla)).getWeather().downfall()));

        builder.temperatureAdjustment(Objects.requireNonNullElse(vanillaBiomeProperties.getTemperatureModifier(),
            ((BiomeAccessor) ((Object) vanilla)).getWeather().temperatureModifier()));

        builder.mobSpawnSettings(Objects.requireNonNullElse(vanillaBiomeProperties.getSpawnSettings(), vanilla.getMobSettings()));

        return builder
            .specialEffects(effects.build())
            .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
            .build();
    }

    public static String createBiomeID(ConfigPack pack, com.dfsek.terra.api.registry.key.RegistryKey biomeID) {
        return pack.getID()
                   .toLowerCase() + "/" + biomeID.getNamespace().toLowerCase(Locale.ROOT) + "/" + biomeID.getID().toLowerCase(Locale.ROOT);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> getTerraBiomeMap() {
        return Map.copyOf(TERRA_BIOME_MAP);
    }
}
