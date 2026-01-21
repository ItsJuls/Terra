package com.dfsek.terra.addons.image.colorsampler.vector;

import com.dfsek.terra.addons.image.colorsampler.ColorSampler;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A ColorSampler that uses vector geometry (JTS Polygons) for infinite-resolution color sampling.
 * This avoids the memory overhead of rasterized images while providing precise spatial queries.
 */
public class VectorColorSampler implements ColorSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSampler.class);

    private final STRtree spatialIndex;
    private final ColorSampler fallback;
    private final GeometryFactory geometryFactory;
    private final double scaleX;
    private final double scaleZ;
    private final double translateX;
    private final double translateZ;

    // Cache prepared geometries for faster containment checks
    private final Map<Polygon, PreparedGeometry> preparedGeometryCache;

    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong hitCount = new AtomicLong(0);
    private volatile long lastLogTime = System.currentTimeMillis();

    /**
     * Creates a new VectorColorSampler.
     *
     * @param polygons List of polygons with color data stored in userData
     * @param fallback Fallback sampler for points not inside any polygon
     * @param svgWidth Width of the SVG viewBox
     * @param svgHeight Height of the SVG viewBox
     * @param worldX1 Minimum world X coordinate
     * @param worldZ1 Minimum world Z coordinate
     * @param worldX2 Maximum world X coordinate
     * @param worldZ2 Maximum world Z coordinate
     */
    public VectorColorSampler(List<Polygon> polygons, ColorSampler fallback,
                              double svgWidth, double svgHeight,
                              double worldX1, double worldZ1,
                              double worldX2, double worldZ2) {
        this.fallback = fallback;
        this.geometryFactory = new GeometryFactory();
        this.spatialIndex = new STRtree();
        this.preparedGeometryCache = new HashMap<>();

        // Calculate transformation from world coordinates to SVG coordinates
        double worldWidth = worldX2 - worldX1;
        double worldHeight = worldZ2 - worldZ1;

        this.scaleX = svgWidth / worldWidth;
        this.scaleZ = svgHeight / worldHeight;
        this.translateX = -worldX1;
        this.translateZ = -worldZ1;

        LOGGER.info("Building spatial index for {} polygons...", polygons.size());
        long startTime = System.currentTimeMillis();

        // Build spatial index and prepare geometries
        PreparedGeometryFactory prepFactory = new PreparedGeometryFactory();
        for (Polygon polygon : polygons) {
            Envelope envelope = polygon.getEnvelopeInternal();
            spatialIndex.insert(envelope, polygon);

            // Pre-compute prepared geometry for faster containment checks
            preparedGeometryCache.put(polygon, prepFactory.create(polygon));
        }

        spatialIndex.build();

        long buildTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Spatial index built in {} ms. VectorColorSampler ready!", buildTime);
        LOGGER.info("Coordinate transform: World[{},{} to {},{}] -> SVG[0,0 to {},{}]",
            worldX1, worldZ1, worldX2, worldZ2, svgWidth, svgHeight);
    }

    @Override
    public int apply(int x, int z) {
        long queries = queryCount.incrementAndGet();

        // Log stats every 10000 queries
        if (queries % 10000 == 0) {
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 5000) { // Only log every 5 seconds minimum
                long hits = hitCount.get();
                double hitRate = (hits * 100.0) / queries;
                LOGGER.info("VectorColorSampler stats: {} queries, {} hits ({}% hit rate)",
                    queries, hits, String.format("%.1f", hitRate));
                lastLogTime = now;
            }
        }

        // Transform world coordinates to SVG space
        double svgX = (x + translateX) * scaleX;
        double svgZ = (z + translateZ) * scaleZ;

        // Create a coordinate for the query (reuse to avoid object creation)
        Coordinate coord = new Coordinate(svgX, svgZ);

        // Query the spatial index for candidate polygons
        @SuppressWarnings("unchecked")
        List<Polygon> candidates = spatialIndex.query(new Envelope(coord));

        // Check each candidate polygon for containment using prepared geometry
        for (Polygon polygon : candidates) {
            PreparedGeometry prepGeom = preparedGeometryCache.get(polygon);
            if (prepGeom != null && prepGeom.contains(geometryFactory.createPoint(coord))) {
                hitCount.incrementAndGet();
                // Return the color stored in the polygon's userData
                Integer color = (Integer) polygon.getUserData();
                if (color != null) {
                    return color;
                }
            }
        }

        // No polygon contains this point, use fallback
        return fallback.apply(x, z);
    }
}
