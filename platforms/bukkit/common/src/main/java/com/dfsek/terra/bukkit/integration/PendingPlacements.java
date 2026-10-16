package com.dfsek.terra.bukkit.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Placements made while chunks generate, waiting for the chunk to load so the owning plugin can place them in the world.
 * <p>
 * Generation can't call Nexo or CustomCrops: it runs off the main thread, in a world that isn't loaded yet. Placements are
 * collected per chunk and applied once the chunk loads, on the thread that owns it. If the plugin isn't enabled yet (Terra
 * generates the spawn chunks before plugins that load after the worlds), or can't place it yet, they wait and are retried.
 * They are saved on shutdown so a restart doesn't lose them.
 */
public final class PendingPlacements implements Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PendingPlacements.class);
    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_TICKS = 20L * 15;
    private static volatile PendingPlacements instance;

    private final Plugin plugin;
    private final Path file;
    private final Map<UUID, Map<Long, List<Entry>>> pending = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private PendingPlacements(Plugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("pending-placements.tsv");
    }

    /**
     * Start collecting placements and applying them as chunks load.
     */
    public static void enable(Plugin plugin) {
        PendingPlacements placements = new PendingPlacements(plugin);
        placements.load();
        Bukkit.getPluginManager().registerEvents(placements, plugin);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> placements.retryLoaded(), RETRY_TICKS, RETRY_TICKS);
        instance = placements;
    }

    public static void disable() {
        PendingPlacements placements = instance;
        if(placements != null) placements.save();
    }

    /**
     * Queue a placement for when its chunk loads. Thread safe; called from generation threads.
     */
    public static void add(World world, int x, int y, int z, Placement placement) {
        PendingPlacements placements = instance;
        if(placements == null) {
            LOGGER.warn("Can't place {} at ({}, {}, {}): Terra isn't enabled", placement.serialize(), x, y, z);
            return;
        }
        placements.queue(world.getUID(), new Entry(x, y, z, placement, 0));
    }

    /**
     * Place something now in a loaded world (e.g. a structure placed by command), on the thread that owns the location.
     */
    public static void placeNow(World world, int x, int y, int z, Placement placement) {
        PendingPlacements placements = instance;
        if(placements == null) return;
        Entry entry = new Entry(x, y, z, placement, 0);
        Bukkit.getRegionScheduler().run(placements.plugin, world, x >> 4, z >> 4, task -> placements.apply(world, List.of(entry)));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private void queue(UUID world, Entry entry) {
        // Lists are only touched inside compute(), which holds the map's lock for that chunk.
        pending.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).compute(key(entry.x >> 4, entry.z >> 4), (k, list) -> {
            List<Entry> entries = list == null ? new ArrayList<>() : list;
            entries.add(entry);
            return entries;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        Map<Long, List<Entry>> chunks = pending.get(world.getUID());
        if(chunks == null || !chunks.containsKey(key(chunkX, chunkZ))) return;
        // Run once the chunk is fully loaded rather than inside the load event.
        Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, task -> flush(world, chunkX, chunkZ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if(name.equals(NexoHook.PLUGIN) || name.equals(CustomCropsHook.PLUGIN)) {
            // Give the plugin a moment to load its content before placing anything.
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> retryLoaded(), 40L);
        }
    }

    /**
     * Retry everything waiting in loaded chunks.
     */
    private void retryLoaded() {
        pending.forEach((worldId, chunks) -> {
            World world = Bukkit.getWorld(worldId);
            if(world == null) return;
            for(Long chunk : chunks.keySet()) {
                int chunkX = (int) (chunk >> 32);
                int chunkZ = (int) (long) chunk;
                if(!world.isChunkLoaded(chunkX, chunkZ)) continue;
                Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, task -> flush(world, chunkX, chunkZ));
            }
        });
    }

    private void flush(World world, int chunkX, int chunkZ) {
        Map<Long, List<Entry>> chunks = pending.get(world.getUID());
        if(chunks == null) return;
        List<Entry> entries = chunks.remove(key(chunkX, chunkZ));
        if(entries != null) apply(world, entries);
    }

    private void apply(World world, List<Entry> entries) {
        for(Entry entry : entries) {
            Placement placement = entry.placement;
            if(!Bukkit.getPluginManager().isPluginEnabled(placement.plugin())) {
                if(Bukkit.getPluginManager().getPlugin(placement.plugin()) == null) {
                    warnOnce(placement.plugin(), "{} isn't installed; dropping {} placed by Terra", placement.plugin(),
                        placement.serialize());
                    continue;
                }
                queue(world.getUID(), entry); // installed but not enabled yet
                continue;
            }
            boolean placed;
            try {
                placed = placement.place(new Location(world, entry.x, entry.y, entry.z));
            } catch(RuntimeException | LinkageError e) {
                LOGGER.warn("Failed to place {} at ({}, {}, {})", placement.serialize(), entry.x, entry.y, entry.z, e);
                continue;
            }
            if(placed) continue;
            if(entry.attempts + 1 < MAX_ATTEMPTS) {
                queue(world.getUID(), new Entry(entry.x, entry.y, entry.z, placement, entry.attempts + 1));
            } else {
                warnOnce(placement.serialize(), "Gave up placing {} (unknown id, or {} doesn't manage world {}?)", placement.serialize(),
                    placement.plugin(), world.getName());
            }
        }
    }

    private void warnOnce(String key, String message, Object... args) {
        if(warned.add(key)) LOGGER.warn(message, args);
    }

    private void load() {
        if(!Files.exists(file)) return;
        try {
            int count = 0;
            for(String line : Files.readAllLines(file)) {
                if(line.isBlank()) continue;
                String[] parts = line.split("\t", 5);
                queue(UUID.fromString(parts[0]), new Entry(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Placement.parse(parts[4]), 0));
                count++;
            }
            Files.delete(file);
            if(count > 0) LOGGER.info("Loaded {} placements waiting for their chunks to load", count);
        } catch(IOException | RuntimeException e) {
            LOGGER.error("Failed to read {}", file, e);
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        pending.forEach((world, chunks) -> chunks.keySet().forEach(chunk -> chunks.computeIfPresent(chunk, (k, entries) -> {
            for(Entry entry : entries) {
                lines.add(world + "\t" + entry.x + "\t" + entry.y + "\t" + entry.z + "\t" + entry.placement.serialize());
            }
            return entries;
        })));
        try {
            if(lines.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
            LOGGER.info("Saved {} placements waiting for their chunks to load", lines.size());
        } catch(IOException e) {
            LOGGER.error("Failed to save {}", file, e);
        }
    }

    private record Entry(int x, int y, int z, Placement placement, int attempts) {
    }
}
