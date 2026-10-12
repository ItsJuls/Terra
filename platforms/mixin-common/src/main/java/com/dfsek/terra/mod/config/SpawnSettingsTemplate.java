package com.dfsek.terra.mod.config;

import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import java.util.List;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;


public class SpawnSettingsTemplate implements ObjectTemplate<SpawnSettings> {
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
