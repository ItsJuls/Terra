package com.dfsek.terra.addons.image.colorsampler.mutate;

import com.dfsek.terra.addons.image.colorsampler.ColorSampler;

import com.dfsek.seismic.math.numericanalysis.interpolation.InterpolationFunctions;

import java.util.function.IntUnaryOperator;


public class ScaleColorSampler implements ColorSampler {

    private final ColorSampler sampler;
    private final double scaleX, scaleZ;
    private final InterpolationMethod method;


    public ScaleColorSampler(ColorSampler sampler, double scaleX, double scaleZ, String method) {
        this.sampler = sampler;
        this.scaleX = scaleX;
        this.scaleZ = scaleZ;
        switch(method.toLowerCase()){
            case "nearest":
                this.method = InterpolationMethod.NEAREST;
                break;
            case "bilinear":
                this.method = InterpolationMethod.BILINEAR;
                break;
            case "bicubic":
                this.method = InterpolationMethod.BICUBIC;
                break;
            default:
                throw new IllegalArgumentException("Unknown interpolation method: " + method);
        }
    }

    @Override
    public int apply(int x, int z) {
        double sx = x / scaleX;
        double sz = z / scaleZ;

        switch (method) {
            case BILINEAR:
                return applyBilinear(sx, sz);
            case BICUBIC:
                return applyBicubic(sx, sz);
            case NEAREST:
            default:
                return sampler.apply((int) sx, (int) sz);
        }
    }

    private int applyBilinear(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;

        int c00 = sampler.apply(x0, z0);
        int c10 = sampler.apply(x0 + 1, z0);
        int c01 = sampler.apply(x0, z0 + 1);
        int c11 = sampler.apply(x0 + 1, z0 + 1);

        int a = bilinearChannel(c00, c10, c01, c11, this::getA, tx, tz);
        int r = bilinearChannel(c00, c10, c01, c11, this::getR, tx, tz);
        int g = bilinearChannel(c00, c10, c01, c11, this::getG, tx, tz);
        int b = bilinearChannel(c00, c10, c01, c11, this::getB, tx, tz);
        return argb(a, r, g, b);
    }

    private int applyBicubic(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;

        int[][] grid = new int[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                grid[row][col] = sampler.apply(x0 - 1 + col, z0 - 1 + row);
            }
        }

        int a = bicubicChannel(grid, this::getA, tx, tz);
        int r = bicubicChannel(grid, this::getR, tx, tz);
        int g = bicubicChannel(grid, this::getG, tx, tz);
        int b = bicubicChannel(grid, this::getB, tx, tz);
        return argb(a, r, g, b);
    }

    private static int bilinearChannel(int c00, int c10, int c01, int c11, IntUnaryOperator channel, double tx, double tz) {
        return (int) InterpolationFunctions.biLerp(
            channel.applyAsInt(c00), channel.applyAsInt(c10),
            channel.applyAsInt(c01), channel.applyAsInt(c11),
            tx, tz);
    }

    // I think the cubic function I had differ slightly from what Seismic has but it doesnt really matter tbh\
    private int bicubicChannel(int[][] grid, IntUnaryOperator channel, double tx, double tz) {
        return clamp(InterpolationFunctions.biCubicLerp(
            channel.applyAsInt(grid[0][0]), channel.applyAsInt(grid[0][1]), channel.applyAsInt(grid[0][2]), channel.applyAsInt(grid[0][3]),
            channel.applyAsInt(grid[1][0]), channel.applyAsInt(grid[1][1]), channel.applyAsInt(grid[1][2]), channel.applyAsInt(grid[1][3]),
            channel.applyAsInt(grid[2][0]), channel.applyAsInt(grid[2][1]), channel.applyAsInt(grid[2][2]), channel.applyAsInt(grid[2][3]),
            channel.applyAsInt(grid[3][0]), channel.applyAsInt(grid[3][1]), channel.applyAsInt(grid[3][2]), channel.applyAsInt(grid[3][3]),
            tx, tz));
    }

    private int getA(int c) { return (c >> 24) & 0xFF; }
    private int getR(int c) { return (c >> 16) & 0xFF; }
    private int getG(int c) { return (c >> 8) & 0xFF; }
    private int getB(int c) { return c & 0xFF; }

    private int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clamp(double val) {
        return Math.max(0, Math.min(255, (int) val));
    }

    private enum InterpolationMethod {
        NEAREST,
        BILINEAR,
        BICUBIC,
    }
}
