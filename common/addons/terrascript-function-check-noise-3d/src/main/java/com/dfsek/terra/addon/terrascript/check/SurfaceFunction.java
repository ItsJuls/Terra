/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addon.terrascript.check;

import com.dfsek.seismic.math.floatingpoint.FloatingPointFunctions;
import com.dfsek.seismic.type.vector.Vector2;

import com.dfsek.terra.addons.chunkgenerator.generation.NoiseChunkGenerator3D;
import com.dfsek.terra.addons.chunkgenerator.generation.math.samplers.Sampler3D;
import com.dfsek.terra.addons.terrascript.parser.lang.ImplementationArguments;
import com.dfsek.terra.addons.terrascript.parser.lang.Returnable;
import com.dfsek.terra.addons.terrascript.parser.lang.Scope;
import com.dfsek.terra.addons.terrascript.parser.lang.functions.Function;
import com.dfsek.terra.addons.terrascript.script.TerraImplementationArguments;
import com.dfsek.terra.addons.terrascript.tokenizer.Position;
import com.dfsek.terra.api.world.WritableWorld;


/**
 * {@code surface(x, z)}: the height of the highest solid terrain block in a column, relative to the structure's origin.
 * <p>
 * It reads the terrain noise rather than the world, so it ignores trees, other structures and caves, and gives the same answer
 * from every chunk. That keeps structures that follow the terrain in one piece when a chunkified stage builds them chunk by chunk.
 * Returns one below the world's minimum height (relative) if the column has no solid terrain.
 */
public class SurfaceFunction implements Function<Number> {
    private final Returnable<Number> x, z;
    private final Position position;

    public SurfaceFunction(Returnable<Number> x, Returnable<Number> z, Position position) {
        this.x = x;
        this.z = z;
        this.position = position;
    }

    @Override
    public Number apply(ImplementationArguments implementationArguments, Scope scope) {
        TerraImplementationArguments arguments = (TerraImplementationArguments) implementationArguments;
        Vector2 xz = Vector2.Mutable.of(x.apply(implementationArguments, scope).doubleValue(),
            z.apply(implementationArguments, scope).doubleValue()).rotate(arguments.getRotation());
        int worldX = arguments.getOrigin().getX() + FloatingPointFunctions.round(xz.getX());
        int worldZ = arguments.getOrigin().getZ() + FloatingPointFunctions.round(xz.getZ());
        return surface(worldX, worldZ, arguments.getWorld()) - arguments.getOrigin().getY();
    }

    static int surface(int x, int z, WritableWorld world) {
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        Sampler3D sampler = ((NoiseChunkGenerator3D) world.getGenerator()).samplerProvider()
            .getChunk(chunkX, chunkZ, world, world.getBiomeProvider());
        int localX = x - (chunkX << 4);
        int localZ = z - (chunkZ << 4);
        for(int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            if(sampler.sample(localX, y, localZ) > 0) return y;
        }
        return world.getMinHeight() - 1;
    }

    @Override
    public Position getPosition() {
        return position;
    }

    @Override
    public ReturnType returnType() {
        return ReturnType.NUMBER;
    }
}
