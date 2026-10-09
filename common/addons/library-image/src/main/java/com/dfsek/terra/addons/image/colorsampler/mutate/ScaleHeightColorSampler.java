package com.dfsek.terra.addons.image.colorsampler.mutate;

import com.dfsek.terra.addons.image.colorsampler.ColorSampler;

public class ScaleHeightColorSampler implements ColorSampler {

    private final ColorSampler sampler;
    private final double scaleX, scaleZ;
    private final InterpolationMethod method;

    public ScaleHeightColorSampler(ColorSampler sampler, double scaleX, double scaleZ, String method) {
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
        // Transform world coordinates to image coordinates
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

        // Fetch RAW height values directly
        int h00 = sampler.apply(x0, z0);
        int h10 = sampler.apply(x0 + 1, z0);
        int h01 = sampler.apply(x0, z0 + 1);
        int h11 = sampler.apply(x0 + 1, z0 + 1);

        return interpolateFourHeights(h00, h10, h01, h11, tx, tz);
    }

    private int applyBicubic(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = x - x0;
        double tz = z - z0;

        double[] rowResults = new double[4];

        for (int i = -1; i <= 2; i++) {
            int h0 = sampler.apply(x0 - 1, z0 + i);
            int h1 = sampler.apply(x0,     z0 + i);
            int h2 = sampler.apply(x0 + 1, z0 + i);
            int h3 = sampler.apply(x0 + 2, z0 + i);
            rowResults[i + 1] = cubic(h0, h1, h2, h3, tx);
        }

        double result = cubic(rowResults[0], rowResults[1], rowResults[2], rowResults[3], tz);
        // Clamp to valid 16-bit range
        return (int) Math.max(0, Math.min(65535, result));
    }

    private int interpolateFourHeights(int h00, int h10, int h01, int h11, double tx, double tz) {
        double top = lerp(h00, h10, tx);
        double bottom = lerp(h01, h11, tx);
        return (int) lerp(top, bottom, tz);
    }

    private double lerp(double start, double end, double t) {
        return start + t * (end - start);
    }

    private double cubic(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    private enum InterpolationMethod {
        NEAREST,
        BILINEAR,
        BICUBIC,
    }
}