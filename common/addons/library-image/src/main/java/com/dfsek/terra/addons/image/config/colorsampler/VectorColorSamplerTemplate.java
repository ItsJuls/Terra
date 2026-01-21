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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template for configuring a VectorColorSampler from YAML configuration.
 * Parses SVG files and converts shapes to JTS polygons with color data.
 */
public class VectorColorSamplerTemplate implements ObjectTemplate<ColorSampler> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorColorSamplerTemplate.class);

    // --- ADDED: Cache to prevent reloading the same SVG multiple times ---
    private static final Map<String, List<Polygon>> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, double[]> DIMENSION_CACHE = new ConcurrentHashMap<>();

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
        // Create a unique key for the cache based on the absolute file path

        String cacheKey = svgPath;

        List<Polygon> polygons;
        double svgWidth;
        double svgHeight;

        // --- CHECK CACHE FIRST ---
        if (SHAPE_CACHE.containsKey(cacheKey)) {
            LOGGER.info("Using cached SVG data for: {}", svgPath);
            polygons = SHAPE_CACHE.get(cacheKey);
            double[] dims = DIMENSION_CACHE.get(cacheKey);
            svgWidth = dims[0];
            svgHeight = dims[1];
        } else {
            // --- NOT IN CACHE: LOAD FROM DISK ---
            long startTime = System.currentTimeMillis();
            LOGGER.info("Loading vector SVG from path: {}", svgPath);

            try {
                // Resolve path relative to config pack
                Path resolvedPath = pack.getRootPath().resolve(svgPath);

                if (!Files.exists(resolvedPath)) {
                    throw new RuntimeException("SVG file not found at path: " + resolvedPath);
                }

                long fileSize = Files.size(resolvedPath);
                LOGGER.info("SVG file size: {} MB ({} bytes)", fileSize / 1024.0 / 1024.0, fileSize);

                // Load SVG file
                LOGGER.info("Parsing SVG file...");
                SVGUniverse universe = new SVGUniverse();
                URI svgUri;

                try (InputStream is = Files.newInputStream(resolvedPath)) {
                    svgUri = universe.loadSVG(is, svgPath);
                }

                SVGDiagram diagram = universe.getDiagram(svgUri);

                if (diagram == null) {
                    throw new RuntimeException("Failed to load SVG from path: " + svgPath);
                }

                // Get SVG dimensions
                svgWidth = diagram.getWidth();
                svgHeight = diagram.getHeight();
                LOGGER.info("SVG dimensions: {}x{}", svgWidth, svgHeight);

                // Parse all shapes from the SVG
                LOGGER.info("Extracting shapes from SVG...");
                polygons = new ArrayList<>();
                parseElement(diagram.getRoot(), polygons);

                if (polygons.isEmpty()) {
                    throw new RuntimeException("No valid shapes found in SVG: " + svgPath);
                }

                // --- SAVE TO CACHE ---
                SHAPE_CACHE.put(cacheKey, polygons);
                DIMENSION_CACHE.put(cacheKey, new double[]{svgWidth, svgHeight});

                long loadTime = System.currentTimeMillis() - startTime;
                LOGGER.info("Successfully loaded {} polygons from SVG in {} ms", polygons.size(), loadTime);

            } catch (IOException e) {
                LOGGER.error("Failed to load SVG file: {}", svgPath, e);
                throw new RuntimeException("Failed to load SVG file: " + svgPath, e);
            } catch (Exception e) {
                LOGGER.error("Failed to create VectorColorSampler: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to create VectorColorSampler: " + e.getMessage(), e);
            }
        }

        LOGGER.info("World bounds: X[{} to {}], Z[{} to {}]", worldX1, worldX2, worldZ1, worldZ2);

        return new VectorColorSampler(
            polygons,
            fallback,
            svgWidth,
            svgHeight,
            worldX1,
            worldZ1,
            worldX2,
            worldZ2
        );
    }

    /**
     * Recursively parse SVG elements to extract shapes with fill OR stroke colors.
     */
    private void parseElement(SVGElement element, List<Polygon> polygons) {
        // Process shape elements
        if (element instanceof ShapeElement shapeElement) {
            try {
                Color effectiveColor = null;
                StyleAttribute styleAttr = new StyleAttribute();

                // 1. Try to get FILL color
                if (shapeElement.getStyle(styleAttr.setName("fill"))) {
                    effectiveColor = styleAttr.getColorValue();
                }

                // 2. If no FILL (or fill is none), try to get STROKE color
                if (effectiveColor == null) {
                    if (shapeElement.getStyle(styleAttr.setName("stroke"))) {
                        effectiveColor = styleAttr.getColorValue();
                    }
                }

                // 3. If we found a color (either fill or stroke), process the shape
                if (effectiveColor != null) {
                    // Convert Color to web color integer (0xRRGGBB)
                    int webColor = (effectiveColor.getRed() << 16) | (effectiveColor.getGreen() << 8) | effectiveColor.getBlue();

                    // Get the shape geometry
                    Shape shape = shapeElement.getShape();
                    if (shape != null) {
                        // Convert AWT Shape to JTS Polygon
                        List<Polygon> shapePolygons = convertShapeToPolygons(shape, webColor);
                        polygons.addAll(shapePolygons);
                    }
                }
            } catch (SVGException e) {
                // Skip shapes with errors
            }
        }

        // Recursively process children
        for (int i = 0; i < element.getNumChildren(); i++) {
            try {
                SVGElement child = element.getChild(i);
                parseElement(child, polygons);
            } catch (Exception e) {
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

        while (!pathIterator.isDone()) {
            int type = pathIterator.currentSegment(coords);

            switch (type) {
                case PathIterator.SEG_MOVETO:
                    // Start a new path
                    if (!currentPath.isEmpty()) {
                        // Finish the previous path
                        Polygon polygon = createPolygonFromPath(currentPath, color);
                        if (polygon != null) {
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
                    // Approximate quadratic curve with line segments
                    currentPath.add(new Coordinate(coords[2], coords[3]));
                    break;

                case PathIterator.SEG_CUBICTO:
                    // Approximate cubic curve with line segments
                    currentPath.add(new Coordinate(coords[4], coords[5]));
                    break;

                case PathIterator.SEG_CLOSE:
                    // Close the current path
                    if (!currentPath.isEmpty()) {
                        Polygon polygon = createPolygonFromPath(currentPath, color);
                        if (polygon != null) {
                            polygons.add(polygon);
                        }
                        currentPath.clear();
                    }
                    break;
            }

            pathIterator.next();
        }

        // Handle any remaining path
        if (!currentPath.isEmpty()) {
            Polygon polygon = createPolygonFromPath(currentPath, color);
            if (polygon != null) {
                polygons.add(polygon);
            }
        }

        return polygons;
    }

    /**
     * Create a JTS Polygon from a list of coordinates.
     */
    private Polygon createPolygonFromPath(List<Coordinate> path, int color) {
        if (path.size() < 3) {
            // Not enough points for a polygon
            return null;
        }

        // Ensure the ring is closed
        Coordinate first = path.getFirst();
        Coordinate last = path.getLast();
        if (!first.equals2D(last)) {
            path.add(new Coordinate(first.x, first.y));
        }

        try {
            // Create a linear ring from the coordinates
            Coordinate[] coords = path.toArray(new Coordinate[0]);
            LinearRing ring = geometryFactory.createLinearRing(coords);

            // Create a polygon (no holes for now)
            Polygon polygon = geometryFactory.createPolygon(ring);

            // Store the color in the polygon's userData
            polygon.setUserData(color);

            return polygon;
        } catch (Exception e) {
            // Invalid geometry, skip
            return null;
        }
    }
}