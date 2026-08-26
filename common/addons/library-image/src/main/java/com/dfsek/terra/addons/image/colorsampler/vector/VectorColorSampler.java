package com.dfsek.terra.addons.image.colorsampler.vector;

import com.dfsek.terra.addons.image.colorsampler.ColorSampler;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * A ColorSampler that uses vector geometry (JTS Polygons) for infinite-resolution color sampling.
 * Avoids the memory overhead of rasterized images while providing precise spatial queries.
 *
 * Uses IndexedPointInAreaLocator rather than PreparedGeometry: it queries against raw
 * Coordinates (no Point allocation per call) and its internal index is lighter weight,
 * since it only needs to answer point-in-polygon, not the full predicate set
 * PreparedGeometry supports.
 */
public class VectorColorSampler implements ColorSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSampler.class);

    private final STRtree spatialIndex;
    private final ColorSampler fallback;
    private final double scaleX;
    private final double scaleZ;
    private final double translateX;
    private final double translateZ;

    private final Map<Polygon, IndexedPointInAreaLocator> locatorCache;

    // Per-thread "last hit" polygon: chunk generation queries are spatially local,
    // so the previous call's polygon is very likely to contain this call's point too.
    // ThreadLocal because generation is parallelized across threads.
    private final ThreadLocal<Polygon> lastHit = new ThreadLocal<>();

    private final LongAdder queryCount = new LongAdder();
    private final LongAdder hitCount = new LongAdder();
    private volatile long lastLogTime = System.currentTimeMillis();

    public VectorColorSampler(List<Polygon> polygons,
                              STRtree spatialIndex,
                              Map<Polygon, IndexedPointInAreaLocator> locatorCache,
                              ColorSampler fallback,
                              double svgWidth, double svgHeight,
                              double worldX1, double worldZ1,
                              double worldX2, double worldZ2) {
        this.fallback = fallback;
        this.spatialIndex = spatialIndex;
        this.locatorCache = locatorCache;

        double worldWidth = worldX2 - worldX1;
        double worldHeight = worldZ2 - worldZ1;

        this.scaleX = svgWidth / worldWidth;
        this.scaleZ = svgHeight / worldHeight;
        this.translateX = -worldX1;
        this.translateZ = -worldZ1;

        LOGGER.info("VectorColorSampler initialized. Coordinate transform: World[{},{} to {},{}] -> SVG[0,0 to {},{}]",
            worldX1, worldZ1, worldX2, worldZ2, svgWidth, svgHeight);
    }

    @Override
    public int apply(int x, int z) {
        queryCount.increment();

        long queries = queryCount.sum();
        if(queries % 10000 == 0) {
            long now = System.currentTimeMillis();
            if(now - lastLogTime > 5000) {
                long hits = hitCount.sum();
                double hitRate = (hits * 100.0) / queries;
                LOGGER.info("VectorColorSampler stats: {} queries, {} hits ({}% hit rate)",
                    queries, hits, String.format("%.1f", hitRate));
                lastLogTime = now;
            }
        }

        double svgX = (x + translateX) * scaleX;
        double svgZ = (z + translateZ) * scaleZ;
        Coordinate coord = new Coordinate(svgX, svgZ);

        // Fast path: check the last polygon this thread hit before touching the tree at all
        Polygon cached = lastHit.get();
        if(cached != null) {
            IndexedPointInAreaLocator locator = locatorCache.get(cached);
            if(locator != null && locator.locate(coord) != Location.EXTERIOR) {
                hitCount.increment();
                Integer color = (Integer) cached.getUserData();
                if(color != null) return color;
            }
        }

        @SuppressWarnings("unchecked")
        List<Polygon> candidates = spatialIndex.query(new Envelope(coord));

        for(Polygon polygon : candidates) {
            IndexedPointInAreaLocator locator = locatorCache.get(polygon);
            if(locator != null && locator.locate(coord) != Location.EXTERIOR) {
                hitCount.increment();
                lastHit.set(polygon);
                Integer color = (Integer) polygon.getUserData();
                if(color != null) return color;
            }
        }

        return fallback.apply(x, z);
    }
}