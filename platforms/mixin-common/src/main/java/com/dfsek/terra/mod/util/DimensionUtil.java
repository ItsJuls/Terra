package com.dfsek.terra.mod.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.DimensionType.MonsterSettings;
import com.dfsek.terra.mod.ModPlatform;
import com.dfsek.terra.mod.config.MonsterSettingsConfig;
import com.dfsek.terra.mod.config.VanillaWorldProperties;
import com.dfsek.terra.mod.implmentation.TerraIntProvider;


public class DimensionUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionUtil.class);

    public static DimensionType createDimension(VanillaWorldProperties vanillaWorldProperties, DimensionType defaultDimension,
                                                ModPlatform platform) {

        MonsterSettingsConfig monsterSettingsConfig;
        if(vanillaWorldProperties.getMonsterSettings() != null) {
            monsterSettingsConfig = vanillaWorldProperties.getMonsterSettings();
        } else {
            monsterSettingsConfig = new MonsterSettingsConfig();
        }

        MonsterSettings monsterSettings = getMonsterSettings(defaultDimension, monsterSettingsConfig);

        if(vanillaWorldProperties.getFixedTime() != null) {
            LOGGER.warn("minecraft.fixed-time can no longer set a specific time of day; the dimension will only have a fixed time.");
        }

        DimensionType.Skybox skybox = defaultDimension.skybox();
        CardinalLighting.Type cardinalLighting = defaultDimension.cardinalLightType();
        if(vanillaWorldProperties.getEffects() != null) {
            Identifier effects = vanillaWorldProperties.getEffects();
            if(effects.equals(BuiltinDimensionTypes.NETHER.identifier())) {
                skybox = DimensionType.Skybox.NONE;
                cardinalLighting = CardinalLighting.Type.NETHER;
            } else if(effects.equals(BuiltinDimensionTypes.END.identifier())) {
                skybox = DimensionType.Skybox.END;
                cardinalLighting = CardinalLighting.Type.DEFAULT;
            } else {
                skybox = DimensionType.Skybox.OVERWORLD;
                cardinalLighting = CardinalLighting.Type.DEFAULT;
            }
        }

        return new DimensionType(
            vanillaWorldProperties.getFixedTime() == null ? defaultDimension.hasFixedTime() : true,
            vanillaWorldProperties.getHasSkyLight() == null ? defaultDimension.hasSkyLight() : vanillaWorldProperties.getHasSkyLight(),
            vanillaWorldProperties.getHasCeiling() == null ? defaultDimension.hasCeiling() : vanillaWorldProperties.getHasCeiling(),
            defaultDimension.hasEnderDragonFight(),
            vanillaWorldProperties.getCoordinateScale() == null
            ? defaultDimension.coordinateScale()
            : vanillaWorldProperties.getCoordinateScale(),
            vanillaWorldProperties.getHeight() == null ? defaultDimension.minY() : vanillaWorldProperties.getHeight().getMin(),
            vanillaWorldProperties.getHeight() == null ? defaultDimension.height() : vanillaWorldProperties.getHeight().getRange(),
            vanillaWorldProperties.getLogicalHeight() == null
            ? defaultDimension.logicalHeight()
            : vanillaWorldProperties.getLogicalHeight(),
            vanillaWorldProperties.getInfiniburn() == null
            ? defaultDimension.infiniburn()
            : platform.blockLookup().getOrThrow(TagKey.create(Registries.BLOCK, vanillaWorldProperties.getInfiniburn())),
            vanillaWorldProperties.getAmbientLight() == null ? defaultDimension.ambientLight() : vanillaWorldProperties.getAmbientLight(),
            monsterSettings,
            skybox,
            cardinalLighting,
            getAttributes(vanillaWorldProperties, monsterSettingsConfig, defaultDimension),
            defaultDimension.timelines(),
            defaultDimension.defaultClock()
        );
    }

    /**
     * Since 1.21.11 many dimension properties are environment attributes. Translate the dimension properties Terra exposes
     * to their attribute equivalents, on top of the attributes of the vanilla dimension.
     */
    private static EnvironmentAttributeMap getAttributes(VanillaWorldProperties vanillaWorldProperties,
                                                         MonsterSettingsConfig monsterSettingsConfig,
                                                         DimensionType defaultDimension) {
        EnvironmentAttributeMap.Builder attributes = EnvironmentAttributeMap.builder().putAll(defaultDimension.attributes());

        if(vanillaWorldProperties.getUltraWarm() != null) {
            boolean ultraWarm = vanillaWorldProperties.getUltraWarm();
            attributes.set(EnvironmentAttributes.WATER_EVAPORATES, ultraWarm);
            attributes.set(EnvironmentAttributes.FAST_LAVA, ultraWarm);
            attributes.set(EnvironmentAttributes.DEFAULT_DRIPSTONE_PARTICLE,
                ultraWarm ? ParticleTypes.DRIPPING_DRIPSTONE_LAVA : ParticleTypes.DRIPPING_DRIPSTONE_WATER);
        }
        if(vanillaWorldProperties.getNatural() != null) {
            attributes.set(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, vanillaWorldProperties.getNatural());
        }
        if(vanillaWorldProperties.getBedWorks() != null) {
            attributes.set(EnvironmentAttributes.BED_RULE,
                vanillaWorldProperties.getBedWorks() ? BedRule.CAN_SLEEP_WHEN_DARK : BedRule.DESTROY_ON_USE);
        }
        if(vanillaWorldProperties.getRespawnAnchorWorks() != null) {
            attributes.set(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, vanillaWorldProperties.getRespawnAnchorWorks());
        }
        if(vanillaWorldProperties.getCloudHeight() != null) {
            attributes.set(EnvironmentAttributes.CLOUD_HEIGHT, vanillaWorldProperties.getCloudHeight().floatValue());
        }
        if(monsterSettingsConfig.getPiglinSafe() != null) {
            attributes.set(EnvironmentAttributes.PIGLINS_ZOMBIFY, !monsterSettingsConfig.getPiglinSafe());
        }
        if(monsterSettingsConfig.getHasRaids() != null) {
            attributes.set(EnvironmentAttributes.CAN_START_RAID, monsterSettingsConfig.getHasRaids());
        }

        return attributes.build();
    }

    @NotNull
    private static MonsterSettings getMonsterSettings(DimensionType defaultDimension, MonsterSettingsConfig monsterSettingsConfig) {
        MonsterSettings defaultMonsterSettings = defaultDimension.monsterSettings();

        return new MonsterSettings(
            monsterSettingsConfig.getMonsterSpawnLight() == null ? defaultMonsterSettings.monsterSpawnLightTest() : new TerraIntProvider(
                monsterSettingsConfig.getMonsterSpawnLight()),
            monsterSettingsConfig.getMonsterSpawnBlockLightLimit() == null
            ? defaultMonsterSettings.monsterSpawnBlockLightLimit()
            : monsterSettingsConfig.getMonsterSpawnBlockLightLimit()
        );
    }
}
