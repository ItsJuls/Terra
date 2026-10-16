package com.dfsek.terra.bukkit.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.dfsek.terra.api.block.state.BlockState;


/**
 * Block strings for other plugins' content:
 * <ul>
 *     <li>{@code nexo:<id>}: a Nexo custom block.</li>
 *     <li>{@code nexo:furniture/<id>[yaw=<degrees>,face=<up|north|...>]}: Nexo furniture.</li>
 *     <li>{@code customcrops:<crop id>[point=<growth points>]}: a CustomCrops crop, registered so it grows.</li>
 * </ul>
 */
public final class PluginBlocks {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginBlocks.class);
    private static final String NEXO = "nexo:";
    private static final String NEXO_FURNITURE = "nexo:furniture/";
    private static final String CUSTOMCROPS = "customcrops:";

    private PluginBlocks() {
    }

    /**
     * Parse a block string for another plugin's content.
     *
     * @return the block state, or null if the string isn't for another plugin.
     */
    public static BlockState parse(String data) {
        String lower = data.toLowerCase(Locale.ROOT);
        if(lower.startsWith(NEXO_FURNITURE)) {
            requirePlugin(NexoHook.PLUGIN, data);
            Parsed parsed = Parsed.of(data.substring(NEXO_FURNITURE.length()), data);
            float yaw = Float.parseFloat(parsed.properties.getOrDefault("yaw", "0"));
            BlockFace face = BlockFace.valueOf(parsed.properties.getOrDefault("face", "up").toUpperCase(Locale.ROOT));
            return new PlacementBlockState(new Placement.NexoFurniture(parsed.id, yaw, face), data);
        }
        if(lower.startsWith(NEXO)) {
            requirePlugin(NexoHook.PLUGIN, data);
            return new NexoBlockState(Parsed.of(data.substring(NEXO.length()), data).id);
        }
        if(lower.startsWith(CUSTOMCROPS)) {
            requirePlugin(CustomCropsHook.PLUGIN, data);
            Parsed parsed = Parsed.of(data.substring(CUSTOMCROPS.length()), data);
            int point = Integer.parseInt(parsed.properties.getOrDefault("point", "0"));
            return new PlacementBlockState(new Placement.CustomCrop(parsed.id, point), data);
        }
        return null;
    }

    private static void requirePlugin(String plugin, String data) {
        // Packs load before plugins that load after the worlds are enabled, so only check that it's installed.
        if(Bukkit.getPluginManager().getPlugin(plugin) == null) {
            throw new IllegalArgumentException("\"" + data + "\" needs " + plugin + ", which isn't installed");
        }
    }

    private record Parsed(String id, Map<String, String> properties) {
        static Parsed of(String rest, String original) {
            int open = rest.indexOf('[');
            if(open < 0) return new Parsed(rest, Map.of());
            int close = rest.indexOf(']', open);
            if(close < 0) throw new IllegalArgumentException("Unclosed [ in \"" + original + "\"");
            Map<String, String> properties = new HashMap<>();
            for(String property : rest.substring(open + 1, close).split(",")) {
                if(property.isBlank()) continue;
                int equals = property.indexOf('=');
                if(equals < 0) throw new IllegalArgumentException("Expected key=value, got \"" + property + "\" in \"" + original + "\"");
                properties.put(property.substring(0, equals).trim().toLowerCase(Locale.ROOT), property.substring(equals + 1).trim());
            }
            return new Parsed(rest.substring(0, open), properties);
        }
    }
}
