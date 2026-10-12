package com.dfsek.terra.addons.generation.feature.config;

import com.dfsek.seismic.type.sampler.Sampler;
import com.dfsek.tectonic.api.config.template.ValidatedConfigTemplate;
import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import com.dfsek.tectonic.api.exception.ValidationException;

import com.dfsek.terra.addons.generation.feature.FeatureGenerationStage;
import com.dfsek.terra.addons.generation.feature.chunkified.ChunkifiedFeatureGenerationStage;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.properties.PropertyKey;
import com.dfsek.terra.api.world.chunk.generation.stage.GenerationStage;


public class FeatureStageTemplate implements ObjectTemplate<GenerationStage>, ValidatedConfigTemplate {
    private static final int MAX_RADIUS = 1024;
    private final Platform platform;
    private final PropertyKey<BiomeFeatures> biomeFeaturesKey;
    @Value("id")
    private String id;

    @Value("resolution")
    @Default
    private int resolution = 4;

    @Value("blend.sampler")
    @Default
    private Sampler blendSampler = new Sampler() {
        @Override
        public double getSample(long seed, double x, double y) {
            return 0;
        }

        @Override
        public double getSample(long seed, double x, double y, double z) {
            return 0;
        }
    };

    @Value("blend.amplitude")
    @Default
    private double blendAmplitude = 0d;

    /**
     * Place structures across as many chunks as they need instead of only the 3x3 chunks around their origin.
     */
    @Value("chunkified")
    @Default
    private boolean chunkified = false;

    /**
     * How far, in blocks, structures in a chunkified stage may reach from their origin horizontally.
     */
    @Value("max-radius")
    @Default
    private int maxRadius = 64;

    public FeatureStageTemplate(Platform platform, PropertyKey<BiomeFeatures> biomeFeaturesKey) {
        this.platform = platform;
        this.biomeFeaturesKey = biomeFeaturesKey;
    }


    @Override
    public FeatureGenerationStage get() {
        if(chunkified) {
            return new ChunkifiedFeatureGenerationStage(platform, id, resolution, biomeFeaturesKey, blendSampler, blendAmplitude,
                maxRadius);
        }
        return new FeatureGenerationStage(platform, id, resolution, biomeFeaturesKey, blendSampler, blendAmplitude);
    }

    @Override
    public boolean validate() throws ValidationException {
        if(!(resolution == 1
             || resolution == 2
             || resolution == 4
             || resolution == 8
             || resolution == 16)) throw new ValidationException(
            "Resolution must be power of 2 less than or equal to 16 (1, 2, 4, 8, 16), got: " + resolution);
        if(maxRadius < 0 || maxRadius > MAX_RADIUS) throw new ValidationException(
            "max-radius must be between 0 and " + MAX_RADIUS + " blocks, got: " + maxRadius);
        return true;
    }
}
