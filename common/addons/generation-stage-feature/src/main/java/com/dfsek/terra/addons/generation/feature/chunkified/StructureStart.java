/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.generation.feature.chunkified;

import com.dfsek.seismic.type.Rotation;

import com.dfsek.terra.api.structure.Structure;
import com.dfsek.terra.api.structure.feature.Feature;


/**
 * A structure placed by a chunkified feature stage. Each chunk the structure reaches generates it again and keeps the part inside
 * itself, so a start only has to know where the structure is and how to recreate it.
 */
final class StructureStart {
    final Feature feature;
    final Structure structure;
    final int x, y, z;
    final long seed;
    final Rotation rotation;

    /**
     * Horizontal area the structure wrote to, once a chunk has generated it. Lets the rest of the chunks in range skip it.
     */
    private volatile Bounds bounds;

    StructureStart(Feature feature, Structure structure, int x, int y, int z, long seed, Rotation rotation) {
        this.feature = feature;
        this.structure = structure;
        this.x = x;
        this.y = y;
        this.z = z;
        this.seed = seed;
        this.rotation = rotation;
    }

    boolean mayIntersect(int chunkMinX, int chunkMinZ, int radius) {
        Bounds known = bounds;
        if(known != null) return known.intersects(chunkMinX, chunkMinZ);
        return x - radius <= chunkMinX + 15 && x + radius >= chunkMinX
               && z - radius <= chunkMinZ + 15 && z + radius >= chunkMinZ;
    }

    void setBounds(Bounds bounds) {
        if(this.bounds == null) this.bounds = bounds;
    }

    record Bounds(int minX, int maxX, int minZ, int maxZ) {
        static final Bounds EMPTY = new Bounds(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);

        boolean intersects(int chunkMinX, int chunkMinZ) {
            return minX <= chunkMinX + 15 && maxX >= chunkMinX && minZ <= chunkMinZ + 15 && maxZ >= chunkMinZ;
        }
    }
}
