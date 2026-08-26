package com.dfsek.terra.addons.image.config.colorsampler;

import com.dfsek.tectonic.api.config.template.annotations.Default;
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
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template for configuring a VectorColorSampler from YAML configuration.
 * Parses SVG files and converts shapes to JTS polygons with color data.
 */
public class VectorColorSamplerTemplate implements ObjectTemplate<ColorSampler> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSamplerTemplate.class);

    // Caches keyed by SVG path, so the same file is only parsed and indexed once
    private static final Map<String, List<Polygon>> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, double[]> DIMENSION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, STRtree> STRTREE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<Polygon, IndexedPointInAreaLocator>> LOCATOR_CACHE = new ConcurrentHashMap<>();

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

    @Value("simplify-tolerance")
    @Default
    private double simplifyTolerance = 0.0;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public VectorColorSamplerTemplate(ConfigPack pack) {
        this.pack = pack;
    }

    @Override
    public ColorSampler get() {
        String cacheKey = svgPath;

        List<Polygon> polygons;
        double svgWidth;
        double svgHeight;

        if(SHAPE_CACHE.containsKey(cacheKey)) {
            LOGGER.info("Using cached SVG data for: {}", svgPath);
            polygons = SHAPE_CACHE.get(cacheKey);
            double[] dims = DIMENSION_CACHE.get(cacheKey);
            svgWidth = dims[0];
            svgHeight = dims[1];
        } else {
            long startTime = System.currentTimeMillis();
            LOGGER.info("Loading vector SVG from path: {}", svgPath);

            try {
                Path resolvedPath = pack.getRootPath().resolve(svgPath);

                if(!Files.exists(resolvedPath)) {
                    throw new RuntimeException("SVG file not found at path: " + resolvedPath);
                }

                long fileSize = Files.size(resolvedPath);
                LOGGER.info("SVG file size: {} MB ({} bytes)", fileSize / 1024.0 / 1024.0, fileSize);

                LOGGER.info("Parsing SVG file...");
                SVGUniverse universe = new SVGUniverse();
                URI svgUri;

                try(InputStream is = Files.newInputStream(resolvedPath)) {
                    svgUri = universe.loadSVG(is, svgPath);
                }

                SVGDiagram diagram = universe.getDiagram(svgUri);

                if(diagram == null) {
                    throw new RuntimeException("Failed to load SVG from path: " + svgPath);
                }

                svgWidth = diagram.getWidth();
                svgHeight = diagram.getHeight();
                LOGGER.info("SVG dimensions: {}x{}", svgWidth, svgHeight);

                LOGGER.info("Extracting shapes from SVG...");
                polygons = new ArrayList<>();
                parseElement(diagram.getRoot(), polygons);

                if(polygons.isEmpty()) {
                    throw new RuntimeException("No valid shapes found in SVG: " + svgPath);
                }

                if(simplifyTolerance > 0) {
                    List<Polygon> simplified = new ArrayList<>();
                    for(Polygon p : polygons) {
                        Object color = p.getUserData();
                        Polygon s = (Polygon) TopologyPreservingSimplifier.simplify(p, simplifyTolerance);
                        s.setUserData(color);
                        simplified.add(s);
                    }
                    polygons = simplified;
                    LOGGER.info("Simplified to {} polygons (tolerance {})", polygons.size(), simplifyTolerance);
                }

                SHAPE_CACHE.put(cacheKey, polygons);
                DIMENSION_CACHE.put(cacheKey, new double[]{ svgWidth, svgHeight });

                long loadTime = System.currentTimeMillis() - startTime;
                LOGGER.info("Successfully loaded {} polygons from SVG in {} ms", polygons.size(), loadTime);
            } catch(IOException e) {
                LOGGER.error("Failed to load SVG file: {}", svgPath, e);
                throw new RuntimeException("Failed to load SVG file: " + svgPath, e);
            } catch(Exception e) {
                LOGGER.error("Failed to create VectorColorSampler: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to create VectorColorSampler: " + e.getMessage(), e);
            }
        }

        // Build (or retrieve cached) spatial index + point-in-area locators.
        // Built once per SVG path, not rebuilt on every get() call.
        List<Polygon> finalPolygons = polygons;
        STRtree spatialIndex = STRTREE_CACHE.computeIfAbsent(cacheKey, key -> buildIndex(finalPolygons));
        Map<Polygon, IndexedPointInAreaLocator> locatorCache = LOCATOR_CACHE.computeIfAbsent(cacheKey,
            key -> buildLocatorCache(finalPolygons));

        LOGGER.info("World bounds: X[{} to {}], Z[{} to {}]", worldX1, worldX2, worldZ1, worldZ2);

        return new VectorColorSampler(
            polygons,
            spatialIndex,
            locatorCache,
            fallback,
            svgWidth,
            svgHeight,
            worldX1,
            worldZ1,
            worldX2,
            worldZ2
        );
    }

    private STRtree buildIndex(List<Polygon> polygons) {
        STRtree tree = new STRtree();
        for(Polygon polygon : polygons) {
            Envelope envelope = polygon.getEnvelopeInternal();
            tree.insert(envelope, polygon);
        }
        tree.build();
        return tree;
    }

    private Map<Polygon, IndexedPointInAreaLocator> buildLocatorCache(List<Polygon> polygons) {
        Map<Polygon, IndexedPointInAreaLocator> cache = new HashMap<>();
        for(Polygon polygon : polygons) {
            cache.put(polygon, new IndexedPointInAreaLocator(polygon));
        }
        return cache;
    }

    /**
     * Recursively parse SVG elements to extract shapes with fill OR stroke colors.
     */
    private void parseElement(SVGElement element, List<Polygon> polygons) {
        if(element instanceof ShapeElement shapeElement) {
            try {
                Color effectiveColor = null;
                StyleAttribute styleAttr = new StyleAttribute();

                if(shapeElement.getStyle(styleAttr.setName("fill"))) {
                    effectiveColor = styleAttr.getColorValue();
                }

                if(effectiveColor == null) {
                    if(shapeElement.getStyle(styleAttr.setName("stroke"))) {
                        effectiveColor = styleAttr.getColorValue();
                    }
                }

                if(effectiveColor != null) {
                    int webColor = (effectiveColor.getRed() << 16) | (effectiveColor.getGreen() << 8) | effectiveColor.getBlue();

                    Shape shape = shapeElement.getShape();
                    if(shape != null) {
                        List<Polygon> shapePolygons = convertShapeToPolygons(shape, webColor);
                        polygons.addAll(shapePolygons);
                    }
                }
            } catch(SVGException e) {
                // Skip shapes with errors
            }
        }

        for(int i = 0; i < element.getNumChildren(); i++) {
            try {
                SVGElement child = element.getChild(i);
                parseElement(child, polygons);
            } catch(Exception e) {
                // Skip problematic children
            }
        }
    }

    /**
     * Convert an AWT Shape to one or more JTS Polygons.
     * Handles complex paths that may contain multiple polygons.
     */
    private List<Polygon> convertShapeToPolygons(Shape shape, int color) {
        List<Polygon> polygons = new ArrayList<>();
        PathIterator pathIterator = shape.getPathIterator(null);

        List<Coordinate> currentPath = new ArrayList<>();
        double[] coords = new double[6];

        while(!pathIterator.isDone()) {
            int type = pathIterator.currentSegment(coords);

            switch(type) {
                case PathIterator.SEG_MOVETO:
                    if(!currentPath.isEmpty()) {
                        Polygon polygon = createPolygonFromPath(currentPath, color);
                        if(polygon != null) {
                            polygons.add(polygon);
                        }
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
                    if(!currentPath.isEmpty()) {
                        Polygon polygon = createPolygonFromPath(currentPath, color);
                        if(polygon != null) {
                            polygons.add(polygon);
                        }
                        currentPath.clear();
                    }
                    break;
            }

            pathIterator.next();
        }

        if(!currentPath.isEmpty()) {
            Polygon polygon = createPolygonFromPath(currentPath, color);
            if(polygon != null) {
                polygons.add(polygon);
            }
        }

        return polygons;
    }

    /**
     * Create a JTS Polygon from a list of coordinates.
     */
    private Polygon createPolygonFromPath(List<Coordinate> path, int color) {
        if(path.size() < 3) {
            return null;
        }

        Coordinate first = path.getFirst();
        Coordinate last = path.getLast();
        if(!first.equals2D(last)) {
            path.add(new Coordinate(first.x, first.y));
        }

        try {
            Coordinate[] coords = path.toArray(new Coordinate[0]);
            LinearRing ring = geometryFactory.createLinearRing(coords);
            Polygon polygon = geometryFactory.createPolygon(ring);
            polygon.setUserData(color);
            return polygon;
        } catch(Exception e) {
            return null;
        }
    }
}