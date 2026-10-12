package com.dfsek.terra.mod.mixin.implementations.compat;

import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.feature.Feature;
import com.dfsek.terra.mod.mixin_ifaces.FloraFeatureHolder;


@Mixin(BiomeGenerationSettings.class)
@Implements(@Interface(iface = FloraFeatureHolder.class, prefix = "terra$"))
public class GenerationSettingsFloraFeaturesMixin {
    private List<Feature> flora;

    public void terra$setFloraFeatures(List<Feature> features) {
        this.flora = features;
    }

    @Inject(method = "getBoneMealFeatures", cancellable = true, at = @At("HEAD"))
    public void inject(CallbackInfoReturnable<List<Feature>> cir) {
        if(flora != null) {
            cir.setReturnValue(flora);
        }
    }
}
