/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.feature;

import com.dfsek.seismic.type.Rotation;
import com.dfsek.seismic.type.sampler.Sampler;

import java.util.List;

import com.dfsek.terra.api.structure.Structure;
import com.dfsek.terra.api.structure.feature.Distributor;
import com.dfsek.terra.api.structure.feature.Feature;
import com.dfsek.terra.api.structure.feature.Locator;
import com.dfsek.terra.api.util.collection.ProbabilityCollection;
import com.dfsek.terra.api.world.WritableWorld;


public class ConfiguredFeature implements Feature {
    private final ProbabilityCollection<Structure> structures;

    private final Sampler structureSelector;
    private final Distributor distributor;
    private final Locator locator;

    private final List<Rotation> rotations;

    private final String id;

    public ConfiguredFeature(ProbabilityCollection<Structure> structures, Sampler structureSelector, Distributor distributor,
                             Locator locator, List<Rotation> rotations, String id) {
        this.rotations = List.copyOf(rotations);
        this.structures = structures;
        this.structureSelector = structureSelector;
        this.distributor = distributor;
        this.locator = locator;
        this.id = id;
    }

    @Override
    public Structure getStructure(WritableWorld world, int x, int y, int z) {
        return structures.get(structureSelector, x, y, z, world.getSeed());
    }

    @Override
    public Rotation getRotation(WritableWorld world, int x, int y, int z) {
        if(rotations.size() == 1) return rotations.getFirst();
        // Hash the location instead of drawing from the structure's random, so adding rotations doesn't change the structures themselves.
        long hash = ((world.getSeed() * 31 + x) * 31 + y) * 31 + z;
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return rotations.get((int) Math.floorMod(hash, (long) rotations.size()));
    }

    @Override
    public Distributor getDistributor() {
        return distributor;
    }

    @Override
    public Locator getLocator() {
        return locator;
    }

    @Override
    public String getID() {
        return id;
    }
}
