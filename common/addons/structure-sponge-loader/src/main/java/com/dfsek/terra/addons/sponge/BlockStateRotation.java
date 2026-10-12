/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.sponge;

import com.dfsek.seismic.type.Rotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;


/**
 * Rotates the properties of a block state string ({@code id[property=value,...]{nbt}}) so directional blocks keep facing the
 * same way relative to a rotated structure.
 */
final class BlockStateRotation {
    // Clockwise, as seen from above.
    private static final List<String> HORIZONTAL = List.of("north", "east", "south", "west");

    private BlockStateRotation() {
    }

    /**
     * Clockwise quarter turns (as seen from above) Terra's rotation turns a horizontal direction by. Terra rotates positions with
     * {@code (x, z) -> (z, -x)} for {@link Rotation#CW_90}, which turns east into north: one counterclockwise turn.
     */
    private static int quarterTurns(Rotation rotation) {
        return switch(rotation) {
            case NONE -> 0;
            case CW_90 -> 3;
            case CW_180 -> 2;
            case CCW_90 -> 1;
        };
    }

    static String rotate(String block, Rotation rotation) {
        int turns = quarterTurns(rotation);
        int open = block.indexOf('[');
        int nbt = block.indexOf('{');
        if(turns == 0 || open < 0 || (nbt >= 0 && nbt < open)) return block;
        int close = block.indexOf(']', open);
        if(close < 0) return block;

        Map<String, String> properties = new LinkedHashMap<>();
        for(String property : block.substring(open + 1, close).split(",")) {
            int equals = property.indexOf('=');
            if(equals < 0) return block;
            properties.put(property.substring(0, equals).trim(), property.substring(equals + 1).trim());
        }

        Map<String, String> rotated = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if(HORIZONTAL.contains(key)) {
                // Connections (fences, walls, panes, redstone, vines, ...) move with the side they're on.
                rotated.put(direction(key, turns), value);
                return;
            }
            rotated.put(key, switch(key) {
                case "facing" -> direction(value, turns);
                case "axis" -> turns % 2 == 1 && !value.equals("y") ? (value.equals("x") ? "z" : "x") : value;
                case "rotation" -> rotation16(value, turns);
                case "shape" -> railShape(value, turns);
                case "orientation" -> directions(value, turns);
                default -> value;
            });
        });

        StringJoiner joiner = new StringJoiner(",", "[", "]");
        rotated.forEach((key, value) -> joiner.add(key + "=" + value));
        return block.substring(0, open) + joiner + block.substring(close + 1);
    }

    private static String direction(String direction, int turns) {
        int index = HORIZONTAL.indexOf(direction);
        if(index < 0) return direction;
        return HORIZONTAL.get((index + turns) % 4);
    }

    /**
     * Rotate every direction in an underscore-separated value, e.g. {@code north_up}.
     */
    private static String directions(String value, int turns) {
        StringJoiner joiner = new StringJoiner("_");
        for(String part : value.split("_")) joiner.add(direction(part, turns));
        return joiner.toString();
    }

    /**
     * Signs, banners and heads: 16 steps per turn, clockwise as seen from above.
     */
    private static String rotation16(String value, int turns) {
        try {
            return Integer.toString(Math.floorMod(Integer.parseInt(value) + 4 * turns, 16));
        } catch(NumberFormatException e) {
            return value;
        }
    }

    /**
     * Rail shapes. Stair shapes ({@code straight}, {@code inner_left}, ...) are relative to the stair and stay as they are.
     */
    private static String railShape(String value, int turns) {
        if(value.startsWith("ascending_")) return "ascending_" + direction(value.substring("ascending_".length()), turns);
        String[] parts = value.split("_");
        if(parts.length != 2 || !HORIZONTAL.contains(parts[0]) || !HORIZONTAL.contains(parts[1])) return value;
        List<String> rotated = new ArrayList<>(List.of(direction(parts[0], turns), direction(parts[1], turns)));
        if(rotated.contains("east") && rotated.contains("west")) return "east_west";
        if(rotated.contains("north") && rotated.contains("south")) return "north_south";
        // Curves are named north/south first: north_east, north_west, south_east, south_west.
        rotated.sort((a, b) -> Boolean.compare(!(a.equals("north") || a.equals("south")), !(b.equals("north") || b.equals("south"))));
        return rotated.get(0) + "_" + rotated.get(1);
    }
}
