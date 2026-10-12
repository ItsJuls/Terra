package com.dfsek.terra.lifecycle.util;

import com.dfsek.terra.mod.data.Codecs;
import com.dfsek.terra.mod.util.MinecraftUtil;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;


public final class RegistryUtil {
    private RegistryUtil() {

    }

    public static void register() {
        MinecraftUtil.registerIntProviderTypes();
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, ResourceLocation.parse("terra:terra"), Codecs.MINECRAFT_CHUNK_GENERATOR_WRAPPER);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, ResourceLocation.parse("terra:terra"), Codecs.TERRA_BIOME_SOURCE);
    }
}
