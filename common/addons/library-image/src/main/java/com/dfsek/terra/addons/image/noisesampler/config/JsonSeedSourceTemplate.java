package com.dfsek.terra.addons.image.noisesampler.config;

import com.dfsek.tectonic.api.config.template.annotations.Default;
import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;

import com.dfsek.terra.addons.image.noisesampler.seed.JsonSeedSource;
import com.dfsek.terra.addons.image.noisesampler.seed.SeedSource;
import com.dfsek.terra.api.config.ConfigPack;


public class JsonSeedSourceTemplate implements ObjectTemplate<SeedSource> {
    private final ConfigPack pack;

    @Value("file")
    protected String file;

    @Value("default-weight")
    @Default
    protected double defaultWeight = 1.0;

    public JsonSeedSourceTemplate(ConfigPack pack) {
        this.pack = pack;
    }

    @Override
    public SeedSource get() {
        return new JsonSeedSource(pack.getRootPath().resolve(file), defaultWeight);
    }
}