package com.dfsek.terra.mod.mixin.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Squid and dolphins at any height in Terra worlds. See {@link TerraWaterSpawns}.
 */
@Mixin(AgeableWaterCreature.class)
public abstract class WaterAnimalSpawnMixin {
    @Inject(method = "checkSurfaceAgeableWaterCreatureSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/LevelAccessor;" +
                     "Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;" +
                     "Lnet/minecraft/util/RandomSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void terra$anyHeight(EntityType<? extends AgeableWaterCreature> type, LevelAccessor world, EntitySpawnReason reason,
                                        BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = TerraWaterSpawns.anyHeight(world, pos);
        if(result != null) cir.setReturnValue(result);
    }
}
