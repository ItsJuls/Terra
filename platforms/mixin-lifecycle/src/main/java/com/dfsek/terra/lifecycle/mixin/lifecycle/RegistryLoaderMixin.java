package com.dfsek.terra.lifecycle.mixin.lifecycle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import com.dfsek.terra.lifecycle.LifecyclePlatform;
import com.dfsek.terra.lifecycle.mixin.access.RegistryLoadTaskAccessor;
import com.dfsek.terra.lifecycle.util.LifecycleUtil;
import com.dfsek.terra.lifecycle.util.RegistryHack;
import com.dfsek.terra.mod.CommonPlatform;
import com.dfsek.terra.mod.ModPlatform;


@Mixin(RegistryDataLoader.class)
public class RegistryLoaderMixin {

    @Unique
    private static final AtomicBoolean LOADING_DYNAMIC_REGISTRIES = new AtomicBoolean(false);

    @Unique
    private static final AtomicReference<List<HolderLookup.RegistryLookup<?>>> CONTEXT_REGISTRIES = new AtomicReference<>(List.of());

    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;" +
                     "Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"))
    private static void loadFromResources(ResourceManager resourceManager, List<HolderLookup.RegistryLookup<?>> contextRegistries,
                                          List<RegistryDataLoader.RegistryData<?>> registriesToLoad, Executor executor,
                                          CallbackInfoReturnable<CompletableFuture<RegistryAccess.Frozen>> cir) {
        boolean loadingBiomes = registriesToLoad.stream().anyMatch(entry -> entry.key() == Registries.BIOME);
        if(loadingBiomes) {
            CONTEXT_REGISTRIES.set(contextRegistries);
        }
        LOADING_DYNAMIC_REGISTRIES.set(loadingBiomes);
    }

    /**
     * Runs once every registry of a load has been populated, right before the registries are frozen.
     */
    @Inject(method = "lambda$load$2(Ljava/util/List;Ljava/util/Map;Ljava/lang/Void;)Lnet/minecraft/core/RegistryAccess$Frozen;",
            at = @At("HEAD"))
    private static void beforeFreeze(List<RegistryLoadTask<?>> loadTasks, Map<ResourceKey<?>, Exception> loadingErrors, Void ignored,
                                     CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        if(loadTasks.stream().noneMatch(task -> getRegistry(task).key() == Registries.BIOME)) return;
        if(LOADING_DYNAMIC_REGISTRIES.getAndSet(false)) {
            ModPlatform platform = CommonPlatform.get();
            platform.getRawConfigRegistry().clear();
            WritableRegistry<Biome> biomes = extractRegistry(loadTasks, Registries.BIOME).orElseThrow();
            WritableRegistry<DimensionType> dimensionTypes = extractRegistry(loadTasks, Registries.DIMENSION_TYPE).orElseThrow();
            WritableRegistry<WorldPreset> worldPresets = extractRegistry(loadTasks, Registries.WORLD_PRESET).orElseThrow();
            WritableRegistry<NoiseGeneratorSettings> chunkGeneratorSettings = extractRegistry(loadTasks,
                Registries.NOISE_SETTINGS).orElseThrow();
            WritableRegistry<MultiNoiseBiomeSourceParameterList> multiNoiseBiomeSourceParameterLists = extractRegistry(loadTasks,
                Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).orElseThrow();
            WritableRegistry<Enchantment> enchantments = extractRegistry(loadTasks, Registries.ENCHANTMENT).orElseThrow();

            LifecyclePlatform.setRegistries(biomes, dimensionTypes, chunkGeneratorSettings, multiNoiseBiomeSourceParameterLists,
                enchantments, blockLookup());
            LifecycleUtil.initialize(biomes, worldPresets);
        }
    }

    /**
     * Block lookup from the load context. Unlike {@link BuiltInRegistries#BLOCK}, its tags are usable before tags are bound.
     */
    @Unique
    @SuppressWarnings("unchecked")
    private static HolderGetter<Block> blockLookup() {
        return CONTEXT_REGISTRIES.get()
            .stream()
            .filter(lookup -> lookup.key().equals(Registries.BLOCK))
            .map(lookup -> (HolderGetter<Block>) lookup)
            .findFirst()
            .orElse(BuiltInRegistries.BLOCK);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <T> WritableRegistry<T> getRegistry(RegistryLoadTask<T> task) {
        return ((RegistryLoadTaskAccessor<T>) task).getRegistry();
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <T> Optional<WritableRegistry<T>> extractRegistry(List<RegistryLoadTask<?>> instance,
                                                                    ResourceKey<Registry<T>> key) {
        List<? extends WritableRegistry<?>> matches = instance
            .stream().map(RegistryLoaderMixin::getRegistry)
            .filter(r -> r.key().equals(key))
            .toList();
        if(matches.size() > 1) {
            throw new IllegalStateException("Illegal number of registries returned: " + matches);
        } else if(matches.isEmpty()) {
            return Optional.empty();
        }
        WritableRegistry<T> registry = (WritableRegistry<T>) matches.getFirst();
        ((RegistryHack) registry).terra_bind();
        return Optional.of(registry);
    }
}
