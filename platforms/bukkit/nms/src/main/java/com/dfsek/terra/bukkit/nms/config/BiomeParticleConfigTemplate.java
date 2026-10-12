package com.dfsek.terra.bukkit.nms.config;

import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.attribute.AmbientParticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;


public class BiomeParticleConfigTemplate implements ObjectTemplate<AmbientParticle> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeParticleConfigTemplate.class);

    @Value("particle")
    @Default
    private String particle = null;

    @Value("probability")
    @Default
    private Float probability = 0.1f;

    private static ParticleOptions parse(String particle) throws CommandSyntaxException {
        return ParticleArgument.readParticle(new StringReader(particle),
            HolderLookup.Provider.create(Stream.of(BuiltInRegistries.PARTICLE_TYPE)));
    }

    @Override
    public AmbientParticle get() {
        if(particle == null) {
            return null;
        }

        try {
            return new AmbientParticle(parse(particle), probability);
        } catch(CommandSyntaxException e) {
            // Block states are written as {id:..., properties:{...}} since 26.1, packs may still use {Name:..., Properties:{...}}
            String upgraded = particle.replaceAll("\\bName:", "id:").replaceAll("\\bProperties:", "properties:");
            if(!upgraded.equals(particle)) {
                try {
                    ParticleOptions options = parse(upgraded);
                    LOGGER.warn("Particle \"{}\" uses the legacy block state format, use \"{}\" instead.", particle, upgraded);
                    return new AmbientParticle(options, probability);
                } catch(CommandSyntaxException ignored) {
                }
            }
            throw new RuntimeException(e);
        }
    }
}
