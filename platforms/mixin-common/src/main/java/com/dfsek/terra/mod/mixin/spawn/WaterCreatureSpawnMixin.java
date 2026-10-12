package com.dfsek.terra.mod.mixin.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Cod, salmon, pufferfish (and tropical fish outside biomes tagged allows_tropical_fish_spawns_at_any_height) at any
 * height in Terra worlds. See {@link TerraWaterSpawns}.
 */
@Mixin(WaterAnimal.class)
public abstract class WaterCreatureSpawnMixin {
    @Inject(method = "checkSurfaceWaterAnimalSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/LevelAccessor;" +
                     "Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;" +
                     "Lnet/minecraft/util/RandomSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void terra$anyHeight(EntityType<? extends WaterAnimal> type, LevelAccessor world, EntitySpawnReason reason,
                                        BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = TerraWaterSpawns.anyHeight(world, pos);
        if(result != null) cir.setReturnValue(result);
    }
}
