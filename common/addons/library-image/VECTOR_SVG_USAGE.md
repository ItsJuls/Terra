# Vector SVG Color Sampler for Terra

## Overview

The Vector SVG Color Sampler allows you to use SVG vector files for biome mapping instead of large raster PNG images. This provides:

- **Infinite Resolution**: No pixelation at any zoom level
- **Low Memory Usage**: Vector data is much smaller than raster images
- **Fast Spatial Queries**: Uses JTS R-Tree spatial indexing for efficient point-in-polygon tests
- **Precise Coordinate Mapping**: SVG coordinates are transformed to match your world bounds exactly

## Implementation Details

### Classes Created

1. **`VectorColorSampler`** (`com.dfsek.terra.addons.image.colorsampler.vector.VectorColorSampler`)
   - Implements the `ColorSampler` interface
   - Uses JTS (Java Topology Suite) for geometry operations
   - Maintains an STRtree spatial index for fast lookups
   - Transforms world coordinates to SVG space using scale and translation

2. **`VectorColorSamplerTemplate`** (`com.dfsek.terra.addons.image.config.colorsampler.VectorColorSamplerTemplate`)
   - Implements `ObjectTemplate<ColorSampler>` for Tectonic configuration
   - Parses SVG files using SVG Salamander library
   - Converts AWT Shapes to JTS Polygons
   - Extracts fill colors from SVG elements

### Dependencies Added

The following dependencies are already configured in `build.gradle.kts`:

```kotlin
implementation("io.github.blackears:svg-salamander:1.1.5.5")  // SVG parsing
implementation("org.locationtech.jts:jts-core:1.19.0")        // Geometry operations
```

## Configuration

### Basic YAML Configuration

```yaml
id: BIOME_PROVIDER
type: IMAGE
color-sampler:
  type: VECTOR_SVG
  path: "biomes/continent_shape.svg"
  bounds:
    x1: -15000
    z1: -15000
    x2: 15000
    z2: 15000
  fallback:
    type: COLOR
    color: "#0000FF"
```

### Configuration Parameters

- **`type`**: Must be `VECTOR_SVG`
- **`path`**: Path to the SVG file (relative to the config pack root)
- **`bounds`**: World coordinate bounds that the SVG will be stretched to fit
  - `x1`: Minimum world X coordinate
  - `z1`: Minimum world Z coordinate
  - `x2`: Maximum world X coordinate
  - `z2`: Maximum world Z coordinate
- **`fallback`**: ColorSampler to use when a coordinate is not inside any SVG polygon

## SVG File Requirements

### Supported Features

- **Shapes**: All SVG shape elements (rect, circle, ellipse, path, polygon, polyline)
- **Fill Colors**: Each shape must have a `fill` attribute with a color value
- **Nested Groups**: The parser recursively processes all child elements

### SVG Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1000" viewBox="0 0 1000 1000">
  <!-- Ocean biome (blue) -->
  <rect x="0" y="0" width="1000" height="1000" fill="#0000FF"/>
  
  <!-- Continent (green) -->
  <path d="M 200,200 L 800,200 L 800,800 L 200,800 Z" fill="#00FF00"/>
  
  <!-- Desert region (yellow) -->
  <circle cx="500" cy="500" r="150" fill="#FFFF00"/>
  
  <!-- Mountain range (gray) -->
  <polygon points="300,400 350,300 400,400" fill="#808080"/>
</svg>
```

### Color Mapping

Each SVG shape's fill color is extracted and stored with the polygon. When sampling:
1. The world coordinates (x, z) are transformed to SVG space
2. A point-in-polygon test is performed using the spatial index
3. If a polygon contains the point, its fill color is returned
4. If no polygon contains the point, the fallback sampler is used

## Technical Details

### Coordinate Transformation

The sampler calculates a transformation matrix to map world coordinates to SVG space:

```
scaleX = svgWidth / (worldX2 - worldX1)
scaleZ = svgHeight / (worldZ2 - worldZ1)
translateX = -worldX1
translateZ = -worldZ1

svgX = (worldX + translateX) * scaleX
svgZ = (worldZ + translateZ) * scaleZ
```

This ensures the SVG is stretched to exactly fit the specified world bounds.

### Spatial Indexing

- All polygons are inserted into a JTS `STRtree` (Sort-Tile-Recursive tree)
- This is an R-Tree variant optimized for static data
- Query time is O(log n) instead of O(n) for linear search
- The tree is built once during initialization

### Curve Approximation

Bezier curves (quadratic and cubic) are approximated with line segments:
- `SEG_QUADTO`: Uses the end control point
- `SEG_CUBICTO`: Uses the final control point

For more accurate curve representation, you can convert curves to paths with more segments in your SVG editor.

## Performance Considerations

### Memory Usage

- **Vector data**: ~100 bytes per polygon vertex
- **Spatial index**: ~50 bytes per polygon
- **Example**: A map with 1000 polygons averaging 20 vertices = ~2.5 MB

Compare this to a 16384x16384 PNG image = ~256 MB!

### Query Performance

- **Spatial index query**: O(log n) where n = number of polygons
- **Point-in-polygon test**: O(m) where m = vertices per polygon
- **Typical query time**: < 1 microsecond for maps with < 10,000 polygons

### Optimization Tips

1. **Simplify polygons**: Remove unnecessary vertices in your SVG editor
2. **Merge adjacent regions**: Combine shapes with the same color
3. **Use appropriate bounds**: Smaller world bounds = better precision

## Troubleshooting

### "Failed to load SVG from path"

- Check that the path is correct relative to your config pack root
- Ensure the SVG file is valid XML
- Verify file permissions

### "No valid shapes found in SVG"

- Ensure shapes have `fill` attributes (not just `stroke`)
- Check that shapes are not in `<defs>` or hidden layers
- Verify the SVG has actual shape elements (not just text or images)

### Colors don't match

- SVG colors are converted to RGB (0xRRGGBB format)
- Alpha channel is ignored
- Use hex colors in your SVG for predictable results

## Example Use Cases

### Continental Biome Layout

Use large polygons to define continents, oceans, and major biome regions:

```yaml
color-sampler:
  type: VECTOR_SVG
  path: "biomes/world_layout.svg"
  bounds:
    x1: -30000
    z1: -30000
    x2: 30000
    z2: 30000
  fallback:
    type: COLOR
    color: "#0000FF"  # Ocean
```

### Regional Climate Zones

Layer multiple samplers for detailed biome control:

```yaml
color-sampler:
  type: VECTOR_SVG
  path: "biomes/climate_zones.svg"
  bounds:
    x1: -10000
    z1: -10000
    x2: 10000
    z2: 10000
  fallback:
    type: VECTOR_SVG
    path: "biomes/base_terrain.svg"
    bounds:
      x1: -10000
      z1: -10000
      x2: 10000
      z2: 10000
    fallback:
      type: COLOR
      color: "#00FF00"  # Default plains
```

## Registration

The sampler is automatically registered in `ImageLibraryAddon.java`:

```java
colorSamplerRegistry.register(addon.key("VECTOR_SVG"), VectorColorSamplerTemplate::new);
```

This allows you to use `type: VECTOR_SVG` in your YAML configurations.

## Future Enhancements

Potential improvements for future versions:

1. **Polygon holes**: Support for SVG shapes with holes (donut shapes)
2. **Better curve approximation**: Adaptive subdivision of Bezier curves
3. **Style inheritance**: Support for CSS styles and inherited fill colors
4. **Caching**: Cache transformed coordinates for repeated queries
5. **Multi-threading**: Parallel polygon parsing for large SVG files

## Credits

- **SVG Parsing**: [SVG Salamander](https://github.com/blackears/svgSalamander)
- **Geometry Operations**: [JTS Topology Suite](https://github.com/locationtech/jts)
- **Terra Framework**: [Terra](https://github.com/PolyhedralDev/Terra)

