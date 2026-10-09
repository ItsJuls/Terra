package com.dfsek.terra.addons.image.config.colorsampler;

import com.dfsek.tectonic.api.config.template.annotations.Value;
import com.dfsek.tectonic.api.config.template.object.ObjectTemplate;
import com.dfsek.terra.addons.image.colorsampler.ColorSampler;
import com.dfsek.terra.addons.image.colorsampler.vector.VectorColorSampler;
import com.dfsek.terra.api.config.ConfigPack;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGElement;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import com.kitfox.svg.ShapeElement;
import com.kitfox.svg.xml.StyleAttribute;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Template for configuring a VectorColorSampler from YAML configuration.
 * Parses SVG files and converts shapes to JTS polygons with color data.
 *
 * Disk cache: parsing a 300 MB SVG and repairing its polygons takes minutes (Ecoregions.svg ~3 min). The finished
 * polygons are written once to a small binary file and read back on later starts (seconds). The cache is keyed on the
 * SVG's path, size and modification time, so editing or replacing the SVG rebuilds it automatically.
 *   location: -Dterra.vector.cacheDir=...   (default: <server folder>/cache/terra-vector)
 *   disable:  -Dterra.vector.cache=false
 * Deleting the cache folder is always safe. The polygons read from the cache are exactly the ones the SVG produced
 * (same coordinates as doubles, same order), so generation does not change.
 */
public class VectorColorSamplerTemplate implements ObjectTemplate<ColorSampler> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSamplerTemplate.class);

    // Static cache should persist across multiple instantiations of the template
    private static final Map<String, CachedSvg> CACHE = new ConcurrentHashMap<>();

    /** Bump when the SVG -> polygon conversion changes, so old disk caches are ignored. */
    private static final int CACHE_FORMAT = 1;
    private static final int CACHE_MAGIC = 0x54564331; // "TVC1"
    private static final boolean DISK_CACHE = !"false".equalsIgnoreCase(System.getProperty("terra.vector.cache"));

    static {
        LOGGER.info("VectorColorSamplerTemplate class loaded. Cache identity: {}", System.identityHashCode(CACHE));
    }


    private record CachedSvg(List<Polygon> polygons, STRtree spatialIndex, Map<Polygon, PreparedGeometry> preparedGeometryCache,
                             double width, double height) {
    }

    /** Polygons + SVG size, before indexing. */
    private record Shapes(List<Polygon> polygons, double width, double height) {
    }

    private final ConfigPack pack;

    @Value("path")
    private String svgPath;

    @Value("bounds.x1")
    private double worldX1;

    @Value("bounds.z1")
    private double worldZ1;

    @Value("bounds.x2")
    private double worldX2;

    @Value("bounds.z2")
    private double worldZ2;

    @Value("fallback")
    private ColorSampler fallback;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public VectorColorSamplerTemplate(ConfigPack pack) {
        this.pack = pack;
    }

    @Override
    public ColorSampler get() {
        Path resolvedPath = pack.getRootPath().resolve(svgPath).toAbsolutePath().normalize();
        String cacheKey = resolvedPath.toString();

        LOGGER.info("Accessing SVG cache for key: '{}'. Cache size: {}. Cache identity: {}",
            cacheKey, CACHE.size(), System.identityHashCode(CACHE));

        CachedSvg cached = CACHE.computeIfAbsent(cacheKey, k -> {
            LOGGER.info("Cache MISS for {}. Loading SVG...", k);
            return loadSvg(resolvedPath);
        });

        LOGGER.info("VectorColorSampler config: World bounds X[{} to {}], Z[{} to {}]", worldX1, worldX2, worldZ1, worldZ2);

        return new VectorColorSampler(
            cached.polygons,
            cached.spatialIndex,
            cached.preparedGeometryCache,
            fallback,
            cached.width,
            cached.height,
            worldX1,
            worldZ1,
            worldX2,
            worldZ2
        );
    }

    private CachedSvg loadSvg(Path path) {
        long startTime = System.currentTimeMillis();
        String pathString = path.toString();
        LOGGER.info("Loading vector SVG from path: {}", pathString);

        try {
            if (!Files.exists(path)) {
                throw new RuntimeException("SVG file not found at path: " + path);
            }
            long fileSize = Files.size(path);
            long fileTime = Files.getLastModifiedTime(path).toMillis();
            LOGGER.info("SVG file size: {} MB ({} bytes)", fileSize / 1024.0 / 1024.0, fileSize);

            Path diskCache = DISK_CACHE ? diskCachePath(path) : null;
            Shapes shapes = null;
            if (diskCache != null && Files.isRegularFile(diskCache)) {
                try {
                    shapes = readDiskCache(diskCache, pathString, fileSize, fileTime);
                    if (shapes != null) {
                        LOGGER.info("Read {} polygons from disk cache {} in {} ms", shapes.polygons.size(), diskCache,
                            System.currentTimeMillis() - startTime);
                    } else {
                        LOGGER.info("Disk cache {} is out of date (SVG changed), rebuilding", diskCache);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not read disk cache {} ({}), rebuilding from the SVG", diskCache, e.toString());
                    shapes = null;
                }
            }
            if (shapes == null) {
                shapes = parseSvg(path);
                if (diskCache != null) {
                    long t = System.currentTimeMillis();
                    try {
                        writeDiskCache(diskCache, shapes, pathString, fileSize, fileTime);
                        LOGGER.info("Wrote disk cache {} ({} MB) in {} ms; next start will skip SVG parsing", diskCache,
                            Files.size(diskCache) / (1024 * 1024), System.currentTimeMillis() - t);
                    } catch (Exception e) {
                        LOGGER.warn("Could not write disk cache {}: {}", diskCache, e.toString());
                    }
                }
            }

            List<Polygon> polygons = shapes.polygons;
            if (polygons.isEmpty()) {
                throw new RuntimeException("No valid shapes found in SVG: " + pathString);
            }

            LOGGER.info("Building spatial index for {} polygons...", polygons.size());
            STRtree spatialIndex = new STRtree();
            Map<Polygon, PreparedGeometry> preparedGeometryCache = new HashMap<>();
            PreparedGeometryFactory prepFactory = new PreparedGeometryFactory();
            Map<Polygon, PreparedGeometry> tempPrepCache = polygons.parallelStream()
                .collect(Collectors.toConcurrentMap(
                    p -> p,
                    prepFactory::create,
                    (existing, replacement) -> existing // Keep existing if duplicate
                ));
            preparedGeometryCache.putAll(tempPrepCache);
            for (Polygon polygon : polygons) {
                spatialIndex.insert(polygon.getEnvelopeInternal(), polygon);
            }
            spatialIndex.build();
            LOGGER.info("Spatial index built.");

            long loadTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Successfully loaded {} polygons from SVG in {} ms", polygons.size(), loadTime);
            return new CachedSvg(polygons, spatialIndex, preparedGeometryCache, shapes.width, shapes.height);

        } catch (IOException e) {
            LOGGER.error("Failed to load SVG file: {}", pathString, e);
            throw new RuntimeException("Failed to load SVG file: " + pathString, e);
        } catch (Exception e) {
            LOGGER.error("Failed to create VectorColorSampler: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create VectorColorSampler: " + e.getMessage(), e);
        }
    }

    /** The original (slow) path: parse the SVG with svgSalamander and convert every filled shape to polygons. */
    private Shapes parseSvg(Path path) throws IOException {
        String pathString = path.toString();
        long t0 = System.currentTimeMillis();
        LOGGER.info("Parsing SVG file...");
        SVGUniverse universe = new SVGUniverse();
        URI svgUri;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(path), 1 << 20)) {
            svgUri = universe.loadSVG(is, pathString);
        }
        SVGDiagram diagram = universe.getDiagram(svgUri);
        if (diagram == null) {
            throw new RuntimeException("Failed to load SVG from path: " + pathString);
        }
        double svgWidth = diagram.getWidth();
        double svgHeight = diagram.getHeight();
        LOGGER.info("SVG dimensions: {}x{} (parsed in {} ms)", svgWidth, svgHeight, System.currentTimeMillis() - t0);

        LOGGER.info("Extracting shapes from SVG...");
        List<ShapeElement> shapeElements = new ArrayList<>();
        collectShapeElements(diagram.getRoot(), shapeElements);
        LOGGER.info("Found {} shape elements. Processing in parallel (first start only, later starts use the disk cache)...",
            shapeElements.size());

        long t1 = System.currentTimeMillis();
        List<Polygon> polygons = shapeElements.parallelStream()
            .map(this::processShapeElement)
            .flatMap(List::stream)
            .collect(Collectors.toList());
        LOGGER.info("Converted shapes to {} polygons in {} ms", polygons.size(), System.currentTimeMillis() - t1);
        universe.clear();
        return new Shapes(polygons, svgWidth, svgHeight);
    }

    // ------------------------------------------------------------------ disk cache

    private static Path diskCachePath(Path svg) {
        try {
            String dir = System.getProperty("terra.vector.cacheDir");
            // default: <server folder>/cache/terra-vector (the folder the server is started in)
            Path base = dir != null && !dir.isBlank() ? Paths.get(dir)
                : Paths.get("").toAbsolutePath().resolve("cache").resolve("terra-vector");
            String name = svg.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
            byte[] h = MessageDigest.getInstance("SHA-256").digest(svg.toString().getBytes(StandardCharsets.UTF_8));
            return base.resolve(name + "-" + HexFormat.of().formatHex(h, 0, 6) + ".polys");
        } catch (Exception e) {
            LOGGER.warn("Vector disk cache disabled: {}", e.toString());
            return null;
        }
    }

    /*
     * Layout (big-endian):
     *   int magic, int format, UTF source path, long size, long mtime, double width, double height, int polygonCount,
     *   per polygon: int color, int ringCount (shell first, then holes),
     *     per ring: int pointCount, pointCount * (double x, double y)
     */
    private static void writeDiskCache(Path file, Shapes shapes, String source, long size, long mtime) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 20))) {
            out.writeInt(CACHE_MAGIC);
            out.writeInt(CACHE_FORMAT);
            out.writeUTF(source);
            out.writeLong(size);
            out.writeLong(mtime);
            out.writeDouble(shapes.width);
            out.writeDouble(shapes.height);
            out.writeInt(shapes.polygons.size());
            byte[] buf = new byte[1 << 16];
            for (Polygon p : shapes.polygons) {
                out.writeInt(p.getUserData() instanceof Integer c ? c : 0);
                int holes = p.getNumInteriorRing();
                out.writeInt(1 + holes);
                buf = writeRing(out, p.getExteriorRing().getCoordinates(), buf);
                for (int i = 0; i < holes; i++) {
                    buf = writeRing(out, p.getInteriorRingN(i).getCoordinates(), buf);
                }
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static byte[] writeRing(DataOutputStream out, Coordinate[] cs, byte[] buf) throws IOException {
        out.writeInt(cs.length);
        int need = cs.length * 16;
        if (buf.length < need) buf = new byte[need];
        ByteBuffer bb = ByteBuffer.wrap(buf, 0, need).order(ByteOrder.BIG_ENDIAN);
        for (Coordinate c : cs) {
            bb.putDouble(c.x);
            bb.putDouble(c.y);
        }
        out.write(buf, 0, need);
        return buf;
    }

    /** Returns null when the cache belongs to a different version of the SVG. */
    private Shapes readDiskCache(Path file, String source, long size, long mtime) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 20))) {
            if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_FORMAT) return null;
            if (!in.readUTF().equals(source) || in.readLong() != size || in.readLong() != mtime) return null;
            double width = in.readDouble(), height = in.readDouble();
            int n = in.readInt();
            int[] colors = new int[n];
            double[][][] rings = new double[n][][];
            for (int i = 0; i < n; i++) {
                colors[i] = in.readInt();
                int rc = in.readInt();
                double[][] r = new double[rc][];
                for (int k = 0; k < rc; k++) {
                    int pc = in.readInt();
                    byte[] raw = new byte[pc * 16];
                    in.readFully(raw);
                    double[] xy = new double[pc * 2];
                    ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN).asDoubleBuffer().get(xy);
                    r[k] = xy;
                }
                rings[i] = r;
            }
            // object construction in parallel, order kept (the R-tree "first match" order must not change)
            Polygon[] polys = new Polygon[n];
            IntStream.range(0, n).parallel().forEach(i -> {
                double[][] r = rings[i];
                LinearRing shell = geometryFactory.createLinearRing(toCoords(r[0]));
                LinearRing[] holes = new LinearRing[r.length - 1];
                for (int k = 1; k < r.length; k++) holes[k - 1] = geometryFactory.createLinearRing(toCoords(r[k]));
                Polygon p = geometryFactory.createPolygon(shell, holes);
                p.setUserData(colors[i]);
                polys[i] = p;
                rings[i] = null;
            });
            List<Polygon> list = new ArrayList<>(n);
            Collections.addAll(list, polys);
            return new Shapes(list, width, height);
        }
    }

    private static Coordinate[] toCoords(double[] xy) {
        Coordinate[] cs = new Coordinate[xy.length / 2];
        for (int j = 0; j < cs.length; j++) cs[j] = new Coordinate(xy[2 * j], xy[2 * j + 1]);
        return cs;
    }

    // ------------------------------------------------------------------ SVG -> polygons (unchanged results)

    /**
     * Recursively collect all ShapeElements from the SVG tree.
     */
    private void collectShapeElements(SVGElement element, List<ShapeElement> collector) {
        if (element instanceof ShapeElement shapeElement) {
            collector.add(shapeElement);
        }

        for (int i = 0; i < element.getNumChildren(); i++) {
            try {
                SVGElement child = element.getChild(i);
                collectShapeElements(child, collector);
            } catch (Exception e) {
                LOGGER.warn("Failed to process child element at index {} of {}: {}", i, element.getId(), e.getMessage());
            }
        }
    }

    /**
     * Process a single ShapeElement to extract polygons.
     * Designed to be thread-safe for parallel execution.
     */
    private List<Polygon> processShapeElement(ShapeElement shapeElement) {
        List<Polygon> result = new ArrayList<>();
        try {
            StyleAttribute styleAttr = new StyleAttribute();
            if (shapeElement.getStyle(styleAttr.setName("fill"))) {
                Color fillColor = styleAttr.getColorValue();
                if (fillColor != null) {
                    int webColor = (fillColor.getRed() << 16) | (fillColor.getGreen() << 8) | fillColor.getBlue();
                    Shape shape = shapeElement.getShape();
                    if (shape != null) {
                        result.addAll(convertShapeToPolygons(shape, webColor));
                    }
                }
            }
        } catch (SVGException e) {
            // Skip shapes with errors
        }
        return result;
    }

    /** One ring -> zero or more valid polygons (buffer(0) repair for invalid rings). */
    private List<Polygon> ringToPolygons(LinearRing ring) {
        try {
            Polygon p = geometryFactory.createPolygon(ring);
            if (p.isValid()) {
                return List.of(p);
            }
            try {
                org.locationtech.jts.geom.Geometry fixed = p.buffer(0);
                if (fixed instanceof Polygon fp) {
                    return List.of(fp);
                } else if (fixed instanceof org.locationtech.jts.geom.MultiPolygon) {
                    List<Polygon> out = new ArrayList<>(fixed.getNumGeometries());
                    for (int i = 0; i < fixed.getNumGeometries(); i++) {
                        out.add((Polygon) fixed.getGeometryN(i));
                    }
                    return out;
                }
            } catch (Exception e) {
                LOGGER.warn("Could not repair invalid polygon.");
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    /**
     * Convert an AWT Shape to one or more JTS Polygons.
     * Handles complex paths that may contain multiple polygons and holes.
     */
    private List<Polygon> convertShapeToPolygons(Shape shape, int color) {
        // 1. Extract raw rings from PathIterator (Fast)
        List<LinearRing> rings = new ArrayList<>();
        PathIterator pathIterator = shape.getPathIterator(null);
        double[] coords = new double[6];
        List<Coordinate> currentPath = new ArrayList<>();

        while (!pathIterator.isDone()) {
            int type = pathIterator.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    if (!currentPath.isEmpty()) {
                        LinearRing ring = createRingFromPath(currentPath);
                        if (ring != null) rings.add(ring);
                        currentPath.clear();
                    }
                    currentPath.add(new Coordinate(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_LINETO:
                    currentPath.add(new Coordinate(coords[0], coords[1]));
                    break;
                case PathIterator.SEG_QUADTO:
                    currentPath.add(new Coordinate(coords[2], coords[3]));
                    break;
                case PathIterator.SEG_CUBICTO:
                    currentPath.add(new Coordinate(coords[4], coords[5]));
                    break;
                case PathIterator.SEG_CLOSE:
                    if (!currentPath.isEmpty()) {
                        LinearRing ring = createRingFromPath(currentPath);
                        if (ring != null) rings.add(ring);
                        currentPath.clear();
                    }
                    break;
            }
            pathIterator.next();
        }
        if (!currentPath.isEmpty()) {
            LinearRing ring = createRingFromPath(currentPath);
            if (ring != null) rings.add(ring);
        }

        // 2. Rings -> valid polygons. isValid()/buffer(0) is the expensive part; shapes with many rings
        //    validate their rings in parallel (encounter order is kept, so the result is identical).
        List<Polygon> candidatePolys = (rings.size() >= 32 ? rings.parallelStream() : rings.stream())
            .map(this::ringToPolygons)
            .flatMap(List::stream)
            .collect(Collectors.toCollection(ArrayList::new));

        if (candidatePolys.isEmpty()) return Collections.emptyList();
        if (candidatePolys.size() == 1) {
            Polygon p = candidatePolys.get(0);
            p.setUserData(color);
            List<Polygon> one = new ArrayList<>(1);
            one.add(p);
            return one;
        }

        // 3. Sort by Area Descending (Largest = Shells, Smallest = Holes). List.sort is stable.
        double[] areas = new double[candidatePolys.size()];
        for (int i = 0; i < areas.length; i++) candidatePolys.get(i).setUserData(i);
        for (int i = 0; i < areas.length; i++) areas[i] = candidatePolys.get(i).getArea();
        candidatePolys.sort((p1, p2) -> Double.compare(areas[(Integer) p2.getUserData()], areas[(Integer) p1.getUserData()]));
        for (Polygon p : candidatePolys) p.setUserData(null);

        // 4. Shell/hole detection. Same rule as before: every polygon (largest first) becomes a hole of the FIRST
        //    earlier shell (in creation order) that contains it and none of whose holes contains it; otherwise it
        //    becomes a new shell. Speed-ups that do not change the answer: a quadtree finds the shells whose box
        //    could contain the polygon (instead of testing every shell), and prepared geometries make the
        //    contains() tests fast (PreparedGeometry.contains == Geometry.contains).
        PreparedGeometryFactory prepFactory = new PreparedGeometryFactory();

        class ShellWithHoles {
            final int order;
            final Polygon shell;
            final PreparedGeometry preparedShell;
            final List<LinearRing> holes = new ArrayList<>();
            final List<PreparedGeometry> preparedHoles = new ArrayList<>();

            ShellWithHoles(int order, Polygon shell) {
                this.order = order;
                this.shell = shell;
                this.preparedShell = prepFactory.create(shell);
            }
        }

        List<ShellWithHoles> shells = new ArrayList<>();
        ShellIndex<ShellWithHoles> shellIndex = new ShellIndex<>();

        for (Polygon p : candidatePolys) {
            ShellWithHoles parent = null;
            Envelope pEnv = p.getEnvelopeInternal();

            @SuppressWarnings("unchecked")
            List<ShellWithHoles> candidates = shellIndex.query(pEnv);
            candidates.sort((x, y) -> Integer.compare(x.order, y.order));

            for (ShellWithHoles s : candidates) {
                if (s.shell.getEnvelopeInternal().contains(pEnv) && s.preparedShell.contains(p)) {
                    boolean insideHole = false;
                    for (int h = 0; h < s.holes.size(); h++) {
                        LinearRing hole = s.holes.get(h);
                        if (hole.getEnvelopeInternal().contains(pEnv)) {
                            PreparedGeometry ph = s.preparedHoles.get(h);
                            if (ph == null) {
                                ph = prepFactory.create(geometryFactory.createPolygon(hole));
                                s.preparedHoles.set(h, ph);
                            }
                            if (ph.contains(p)) {
                                insideHole = true;
                                break;
                            }
                        }
                    }
                    if (!insideHole) {
                        parent = s;
                        break;
                    }
                }
            }

            if (parent != null) {
                parent.holes.add(p.getExteriorRing());
                parent.preparedHoles.add(null);
            } else {
                ShellWithHoles newShell = new ShellWithHoles(shells.size(), p);
                shells.add(newShell);
                shellIndex.insert(p.getEnvelopeInternal(), newShell);
            }
        }

        // 5. Build Final Polygons
        List<Polygon> result = new ArrayList<>(shells.size());
        for (ShellWithHoles s : shells) {
            LinearRing[] holesArray = s.holes.toArray(new LinearRing[0]);
            Polygon finalPoly = geometryFactory.createPolygon(s.shell.getExteriorRing(), holesArray);
            finalPoly.setUserData(color);
            result.add(finalPoly);
        }

        return result;
    }

    /**
     * Growing envelope index: an STRtree over older items (rebuilt now and then, since an STRtree is read-only once
     * queried) plus a short list of recent items that is scanned directly. query() returns every item whose envelope
     * intersects the search box (a superset of the ones that can contain it); items with non-finite envelopes are
     * always returned, so nothing the plain linear scan would have accepted can be missed.
     */
    private static final class ShellIndex<T> {
        private final List<T> all = new ArrayList<>();
        private final List<Envelope> envs = new ArrayList<>();
        private final List<T> odd = new ArrayList<>();
        private STRtree tree = null;
        private int treeSize = 0;

        void insert(Envelope e, T item) {
            if (!Double.isFinite(e.getMinX()) || !Double.isFinite(e.getMaxX())
                || !Double.isFinite(e.getMinY()) || !Double.isFinite(e.getMaxY()) || e.isNull()) {
                odd.add(item);
                return;
            }
            all.add(item);
            envs.add(e);
        }

        @SuppressWarnings("unchecked")
        List<T> query(Envelope q) {
            int recent = all.size() - treeSize;
            if (recent > 64 && recent > treeSize / 4) {
                tree = new STRtree();
                for (int i = 0; i < all.size(); i++) tree.insert(envs.get(i), all.get(i));
                tree.build();
                treeSize = all.size();
            }
            List<T> out = new ArrayList<>(odd);
            if (tree != null) out.addAll((List<T>) tree.query(q));
            for (int i = treeSize; i < all.size(); i++) {
                if (envs.get(i).intersects(q)) out.add(all.get(i));
            }
            return out;
        }
    }

    private LinearRing createRingFromPath(List<Coordinate> path) {
        if (path.size() < 3) {
            return null;
        }

        Coordinate first = path.getFirst();
        Coordinate last = path.getLast();
        if (!first.equals2D(last)) {
            path.add(new Coordinate(first.x, first.y));
        }

        try {
            Coordinate[] coords = path.toArray(new Coordinate[0]);
            return geometryFactory.createLinearRing(coords);
        } catch (Exception e) {
            return null;
        }
    }
}
