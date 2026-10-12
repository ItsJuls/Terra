package com.dfsek.terra.addons.image.colorsampler.vector;

import com.dfsek.terra.addons.image.colorsampler.ColorSampler;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;


/**
 * A ColorSampler that uses vector geometry (JTS Polygons) for infinite-resolution color sampling.
 * <p>
 * Answers are cached per 64 x 64 block tile:
 * <ul>
 *   <li>A tile that lies completely inside one region (almost every tile for an ecoregion map) stores a single
 *       colour, so a query is one array read. No R-tree query, no point-in-polygon test, no allocation.</li>
 *   <li>A tile on a region border is resolved once per block (4096 point tests against only the few polygons
 *       touching that tile) and then also served from the array.</li>
 * </ul>
 * Results are the same as asking the polygons directly: a block takes the colour of the first polygon in R-tree
 * order whose {@link IndexedPointInAreaLocator} says it is not EXTERIOR (edges count as inside). They do not depend
 * on which thread asks first, unlike a per-thread "last hit" shortcut, which can pick a different polygon where
 * polygons overlap.
 * <p>
 * Stats are off by default (a shared counter on every call costs more than the lookup itself);
 * enable with -Dterra.vector.stats=true.
 */
public class VectorColorSampler implements ColorSampler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSampler.class);

    private static final int TILE_SHIFT = 6;                  // 64 x 64 blocks
    private static final int TILE = 1 << TILE_SHIFT;
    private static final int TILE_MASK = TILE - 1;
    private static final int SLOT_BITS = 13;                  // 8192 cached tiles (border tiles 16 KB each)
    private static final int NONE = 0x00C0FFEE;               // "no polygon here" -> fallback (alpha 0, not an SVG fill)
    private static final boolean STATS = Boolean.getBoolean("terra.vector.stats");

    /** Polygon with its point locator; the prepared geometry is only built if a tile ever needs the uniform test. */
    private static final class Entry {
        final Polygon polygon;
        final IndexedPointInAreaLocator locator;
        final int color;
        volatile PreparedGeometry prepared;

        Entry(Polygon polygon, IndexedPointInAreaLocator locator, int color) {
            this.polygon = polygon;
            this.locator = locator;
            this.color = color;
        }

        PreparedGeometry prepared() {
            PreparedGeometry p = prepared;
            if(p == null) prepared = p = PreparedGeometryFactory.prepare(polygon);
            return p;
        }
    }

    /** Immutable cached tile; colors == null means every block in it has {@code uniform}. */
    private record Tile(long key, int[] colors, int uniform) {
    }

    private final STRtree index = new STRtree();
    private final ColorSampler fallback;
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final double scaleX, scaleZ, translateX, translateZ;
    private final AtomicReferenceArray<Tile> cache = new AtomicReferenceArray<>(1 << SLOT_BITS);

    private final LongAdder queries = new LongAdder();
    private final LongAdder tilesBuilt = new LongAdder();
    private final LongAdder uniformTiles = new LongAdder();
    private volatile long lastLog = System.currentTimeMillis();

    /**
     * Same signature as before. The polygons are re-indexed together with their locator and colour, so a query
     * never needs a HashMap lookup (JTS Polygon hashCode/equals are not cheap). The STRtree is built from the
     * polygon list in the same order as the template's, so ties between overlapping polygons resolve the same way.
     */
    public VectorColorSampler(List<Polygon> polygons,
                              STRtree spatialIndex,
                              Map<Polygon, IndexedPointInAreaLocator> locatorCache,
                              ColorSampler fallback,
                              double svgWidth, double svgHeight,
                              double worldX1, double worldZ1,
                              double worldX2, double worldZ2) {
        this.fallback = fallback;
        this.scaleX = svgWidth / (worldX2 - worldX1);
        this.scaleZ = svgHeight / (worldZ2 - worldZ1);
        this.translateX = -worldX1;
        this.translateZ = -worldZ1;

        int n = 0;
        for(Polygon p : polygons) {
            if(!(p.getUserData() instanceof Integer color)) continue;
            IndexedPointInAreaLocator locator = locatorCache.get(p);   // one lookup here, never per query
            if(locator == null) locator = new IndexedPointInAreaLocator(p);
            index.insert(p.getEnvelopeInternal(), new Entry(p, locator, color));
            n++;
        }
        index.build();

        LOGGER.info("VectorColorSampler initialized: {} polygons, {}x{} block tile cache ({} slots). " +
                    "Coordinate transform: World[{},{} to {},{}] -> SVG[0,0 to {},{}]",
            n, TILE, TILE, 1 << SLOT_BITS, worldX1, worldZ1, worldX2, worldZ2, svgWidth, svgHeight);
    }

    @Override
    public int apply(int x, int z) {
        if(STATS) stats();
        int tx = x >> TILE_SHIFT, tz = z >> TILE_SHIFT;          // arithmetic shift = floor, fine for negatives
        long key = ((long) tx << 32) | (tz & 0xFFFFFFFFL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - SLOT_BITS));
        Tile t = cache.get(slot);
        if(t == null || t.key != key) {
            t = buildTile(tx, tz, key);
            cache.set(slot, t);                                   // a race only rebuilds the same immutable tile
        }
        int c = t.colors == null ? t.uniform : t.colors[((z & TILE_MASK) << TILE_SHIFT) | (x & TILE_MASK)];
        return c != NONE ? c : fallback.apply(x, z);
    }

    private Tile buildTile(int tx, int tz, long key) {
        if(STATS) tilesBuilt.increment();
        int x0 = tx << TILE_SHIFT, z0 = tz << TILE_SHIFT;
        double ax = (x0 + translateX) * scaleX, bx = (x0 + TILE - 1 + translateX) * scaleX;
        double az = (z0 + translateZ) * scaleZ, bz = (z0 + TILE - 1 + translateZ) * scaleZ;
        Envelope env = new Envelope(ax, bx, az, bz);              // footprint of the tile's sample points

        @SuppressWarnings("unchecked")
        List<Entry> found = index.query(env);                     // R-tree order = the order a point query visits
        List<Entry> cands = new ArrayList<>(found.size());
        for(Entry e : found) if(e.polygon.getEnvelopeInternal().intersects(env)) cands.add(e);

        if(cands.isEmpty()) return uniform(key, NONE);

        // fast path: the first polygon a point query would test covers the whole tile -> it wins everywhere
        Geometry rect = geometryFactory.toGeometry(env);
        if(cands.get(0).prepared().covers(rect)) return uniform(key, cands.get(0).color);

        int[] colors = new int[TILE * TILE];
        Coordinate coord = new Coordinate();
        boolean same = true;
        for(int dz = 0; dz < TILE; dz++) {
            coord.y = (z0 + dz + translateZ) * scaleZ;
            for(int dx = 0; dx < TILE; dx++) {
                coord.x = (x0 + dx + translateX) * scaleX;
                int c = NONE;
                for(int i = 0, n = cands.size(); i < n; i++) {
                    Entry e = cands.get(i);
                    if(e.locator.locate(coord) != Location.EXTERIOR) {
                        c = e.color;
                        break;
                    }
                }
                int idx = (dz << TILE_SHIFT) | dx;
                colors[idx] = c;
                if(c != colors[0]) same = false;
            }
        }
        return same ? uniform(key, colors[0]) : new Tile(key, colors, 0);
    }

    private Tile uniform(long key, int color) {
        if(STATS) uniformTiles.increment();
        return new Tile(key, null, color);
    }

    private void stats() {
        queries.increment();
        long now = System.currentTimeMillis();
        if(now - lastLog > 10_000) {
            lastLog = now;
            LOGGER.info("VectorColorSampler stats: {} queries, {} tiles built ({} single-colour)",
                queries.sum(), tilesBuilt.sum(), uniformTiles.sum());
        }
    }
}
