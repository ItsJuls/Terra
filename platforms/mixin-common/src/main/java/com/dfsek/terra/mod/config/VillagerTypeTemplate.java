package com.dfsek.terra.mod.config;

import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerType;


public class VillagerTypeTemplate implements ObjectTemplate<ResourceKey<VillagerType>> {
    @Value("id")
    @Default
    private ResourceLocation id = null;

    @Override
    public ResourceKey<VillagerType> get() {
        return ResourceKey.create(Registries.VILLAGER_TYPE, id);
    }
}
