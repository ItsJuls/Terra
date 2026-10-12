package com.dfsek.terra.mod.mixin.spawn;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Cod, salmon, pufferfish (and tropical fish outside biomes tagged allows_tropical_fish_spawns_at_any_height) at any
 * height in Terra worlds. See {@link TerraWaterSpawns}.
 */
@Mixin(WaterCreatureEntity.class)
public abstract class WaterCreatureSpawnMixin {
    @Inject(method = "canSpawn(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/WorldAccess;" +
                     "Lnet/minecraft/entity/SpawnReason;Lnet/minecraft/util/math/BlockPos;" +
                     "Lnet/minecraft/util/math/random/Random;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void terra$anyHeight(EntityType<? extends WaterCreatureEntity> type, WorldAccess world, SpawnReason reason,
                                        BlockPos pos, Random random, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = TerraWaterSpawns.anyHeight(world, pos);
        if(result != null) cir.setReturnValue(result);
    }
}
