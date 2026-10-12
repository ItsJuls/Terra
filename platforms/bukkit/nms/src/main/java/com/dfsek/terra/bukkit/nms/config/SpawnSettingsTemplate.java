package com.dfsek.terra.bukkit.nms.config;

import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class SpawnSettingsTemplate implements ObjectTemplate<SpawnSettings> {

    private static final Logger logger = LoggerFactory.getLogger(SpawnTypeConfig.class);
    private static boolean used = false;

    @Value("spawns")
    @Default
    private List<SpawnTypeConfig> spawns = null;

    @Value("costs")
    @Default
    private List<SpawnCostConfig> costs = null;

    @Value("probability")
    @Default
    private Float probability = null;

    @Override
    public SpawnSettings get() {
        MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
        for(SpawnTypeConfig spawn : spawns) {
            MobCategory group = spawn.getGroup();
            WeightedList.Builder<SpawnerData> entries = WeightedList.builder();
            for(SpawnEntryConfig entry : spawn.getEntries()) {
                entries.add(entry.getSpawnEntry(), entry.getWeight());
            }
            builder.addAllSpawns(group, entries.build());
        }
        for(SpawnCostConfig cost : costs) {
            builder.addMobSpawnCost(cost.getType(), cost.getMass(), cost.getGravity());
        }
        return new SpawnSettings(builder.build(), probability);
    }
}
