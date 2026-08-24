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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Template for configuring a VectorColorSampler from YAML configuration.
 * Parses SVG files and converts shapes to JTS polygons with color data.
 */
public class VectorColorSamplerTemplate implements ObjectTemplate<ColorSampler> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSamplerTemplate.class);

    // --- ADDED: Cache to prevent reloading the same SVG multiple times ---
    // Static cache should persist across multiple instantiations of the template
    private static final Map<String, CachedSvg> CACHE = new ConcurrentHashMap<>();

    static {
        LOGGER.info("VectorColorSamplerTemplate class loaded. Cache identity: {}", System.identityHashCode(CACHE));
    }


    private record CachedSvg(List<Polygon> polygons, STRtree spatialIndex, Map<Polygon, PreparedGeometry> preparedGeometryCache,
                             double width, double height) {
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
        // Resolve absolute path to use as cache key to ensure uniqueness and correctness
        Path resolvedPath = pack.getRootPath().resolve(svgPath).toAbsolutePath().normalize();
        String cacheKey = resolvedPath.toString();

        // Debug logging to verify cache behavior and classloader consistency
        LOGGER.info("Accessing SVG cache for key: '{}'. Cache size: {}. Cache identity: {}",
            cacheKey, CACHE.size(), System.identityHashCode(CACHE));

        // Use computeIfAbsent to ensure atomic loading - only one thread will load the SVG for a given key
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
            LOGGER.info("SVG file size: {} MB ({} bytes)", fileSize / 1024.0 / 1024.0, fileSize);

            // Load SVG file
            LOGGER.info("Parsing SVG file...");
            SVGUniverse universe = new SVGUniverse();
            URI svgUri;

            try (InputStream is = Files.newInputStream(path)) {
                svgUri = universe.loadSVG(is, pathString);
            }

            SVGDiagram diagram = universe.getDiagram(svgUri);

            if (diagram == null) {
                throw new RuntimeException("Failed to load SVG from path: " + pathString);
            }

            // Get SVG dimensions
            double svgWidth = diagram.getWidth();
            double svgHeight = diagram.getHeight();
            LOGGER.info("SVG dimensions: {}x{}", svgWidth, svgHeight);

            // Parse all shapes from the SVG
            LOGGER.info("Extracting shapes from SVG...");

            // 1. Collect all ShapeElements first
            List<ShapeElement> shapeElements = new ArrayList<>();
            collectShapeElements(diagram.getRoot(), shapeElements);

            LOGGER.info("Found {} shape elements. Processing in parallel...", shapeElements.size());

            // 2. Process them in parallel
            List<Polygon> polygons = shapeElements.parallelStream()
                .map(this::processShapeElement)
                .flatMap(List::stream)
                .collect(Collectors.toList());

            if (polygons.isEmpty()) {
                throw new RuntimeException("No valid shapes found in SVG: " + pathString);
            }

            // --- BUILD SPATIAL INDEX AND PREPARED GEOMETRY CACHE HERE ---
            LOGGER.info("Building spatial index for {} polygons...", polygons.size());
            STRtree spatialIndex = new STRtree();
            Map<Polygon, PreparedGeometry> preparedGeometryCache = new HashMap<>();
            PreparedGeometryFactory prepFactory = new PreparedGeometryFactory();

            // Building the index is fast, but prepared geometry creation can be slow.
            // We can parallelize prepared geometry creation too if needed, but let's stick to serial for the map put for now.
            // Actually, we can generate PreparedGeometry in parallel and then collect.

            // Let's do a parallel stream to create PreparedGeometries
            // Use a merge function (p1, p2) -> p1 to handle duplicate keys (identical polygons)
            Map<Polygon, PreparedGeometry> tempPrepCache = polygons.parallelStream()
                .collect(Collectors.toConcurrentMap(
                    p -> p,
                    prepFactory::create,
                    (existing, replacement) -> existing // Keep existing if duplicate
                ));

            preparedGeometryCache.putAll(tempPrepCache);

            for (Polygon polygon : polygons) {
                Envelope envelope = polygon.getEnvelopeInternal();
                spatialIndex.insert(envelope, polygon);
            }
            spatialIndex.build();
            LOGGER.info("Spatial index built.");
            // ------------------------------------------------------------

            long loadTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Successfully loaded {} polygons from SVG in {} ms", polygons.size(), loadTime);

            return new CachedSvg(polygons, spatialIndex, preparedGeometryCache, svgWidth, svgHeight);

        } catch (IOException e) {
            LOGGER.error("Failed to load SVG file: {}", pathString, e);
            throw new RuntimeException("Failed to load SVG file: " + pathString, e);
        } catch (Exception e) {
            LOGGER.error("Failed to create VectorColorSampler: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create VectorColorSampler: " + e.getMessage(), e);
        }
    }

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

            // 1. Handle FILL
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

        // 2. Convert Rings to Polygons and Fix Topology (Expensive but necessary)
        List<Polygon> candidatePolys = new ArrayList<>();
        for (LinearRing ring : rings) {
            try {
                Polygon p = geometryFactory.createPolygon(ring);
                if (p.isValid()) {
                    candidatePolys.add(p);
                } else {
                    // Attempt buffer(0) fix
                    try {
                        org.locationtech.jts.geom.Geometry fixed = p.buffer(0);
                        if (fixed instanceof Polygon) {
                            candidatePolys.add((Polygon) fixed);
                        } else if (fixed instanceof org.locationtech.jts.geom.MultiPolygon) {
                            for (int i = 0; i < fixed.getNumGeometries(); i++) {
                                candidatePolys.add((Polygon) fixed.getGeometryN(i));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        if (candidatePolys.isEmpty()) return Collections.emptyList();
        if (candidatePolys.size() == 1) {
            Polygon p = candidatePolys.get(0);
            p.setUserData(color);
            return Collections.singletonList(p);
        }

        // 3. Sort by Area Descending (Largest = Shells, Smallest = Holes)
        candidatePolys.sort((p1, p2) -> Double.compare(p2.getArea(), p1.getArea()));

        // 4. Optimized Shell/Hole Detection using STRtree
        // We put identified SHELLS into an index so we can quickly find parents for holes.
        STRtree shellIndex = new STRtree();

        class ShellWithHoles {
            final Polygon shell;
            // Use PreparedGeometry for fast containment checks
            final PreparedGeometry preparedShell;
            final List<LinearRing> holes = new ArrayList<>();

            ShellWithHoles(Polygon shell) {
                this.shell = shell;
                this.preparedShell = new PreparedGeometryFactory().create(shell);
            }
        }

        List<ShellWithHoles> shells = new ArrayList<>();

        for (Polygon p : candidatePolys) {
            ShellWithHoles parent = null;
            Envelope pEnv = p.getEnvelopeInternal();

            // Query the index for potential parents (instead of looping all shells)
            @SuppressWarnings("unchecked")
            List<ShellWithHoles> candidates = shellIndex.query(pEnv);

            for (ShellWithHoles s : candidates) {
                // Check exact containment using PreparedGeometry (Fast)
                if (s.shell.getEnvelopeInternal().contains(pEnv) && s.preparedShell.contains(p)) {
                    // It is inside this shell. Now check if it is inside an EXISTING hole.
                    // (An island inside a lake inside an island)
                    // If it's inside a hole, it's actually a NEW shell, not a hole.
                    boolean insideHole = false;
                    for (LinearRing hole : s.holes) {
                        if (hole.getEnvelopeInternal().contains(pEnv)) {
                            // We don't cache holes in prepared geom because there are usually few
                            Polygon holePoly = geometryFactory.createPolygon(hole);
                            if (holePoly.contains(p)) {
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
            } else {
                // It's a new shell
                ShellWithHoles newShell = new ShellWithHoles(p);
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

    private LinearRing createRingFromPath(List<Coordinate> path) {
        if (path.size() < 3) {
            return null;
        }

        // Ensure the ring is closed
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