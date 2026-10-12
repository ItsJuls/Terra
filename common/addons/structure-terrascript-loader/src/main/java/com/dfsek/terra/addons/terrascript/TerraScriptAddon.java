/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra Core Addons are licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in this module's root directory.
 */

package com.dfsek.terra.addons.terrascript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.addons.terrascript.loot.LootTableImpl;
import com.dfsek.terra.addons.terrascript.parser.exceptions.ParseException;
import com.dfsek.terra.addons.terrascript.parser.lang.functions.FunctionBuilder;
import com.dfsek.terra.addons.terrascript.script.StructureScript;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.event.events.config.pack.ConfigPackPreLoadEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.inject.annotations.Inject;
import com.dfsek.terra.api.registry.CheckedRegistry;
import com.dfsek.terra.api.structure.LootTable;
import com.dfsek.terra.api.structure.Structure;
import com.dfsek.terra.api.util.FileUtil;


public class TerraScriptAddon implements AddonInitializer {
    @Inject
    private Platform platform;

    @Inject
    private BaseAddon addon;

    @Override
    public void initialize() {
        platform.getEventManager()
            .getHandler(FunctionalEventHandler.class)
            .register(addon, ConfigPackPreLoadEvent.class)
            .then(event -> {
                CheckedRegistry<Structure> structureRegistry = event.getPack().getOrCreateRegistry(Structure.class);
                CheckedRegistry<LootTable> lootRegistry = event.getPack().getOrCreateRegistry(LootTable.class);
                loadLootTables(event.getPack(), lootRegistry);
                try {
                    FileUtil.filesWithExtension(event.getPack().getRootPath(), ".tesf")
                        .entrySet()
                        .stream()
                        .parallel()
                        .map(entry -> {
                            try {
                                String id = FileUtil.fileName(entry.getKey());
                                return new StructureScript(Files.newInputStream(entry.getValue()),
                                    addon.key(id),
                                    platform,
                                    structureRegistry,
                                    lootRegistry,
                                    event.getPack().getOrCreateRegistry(FunctionBuilder.class));
                            } catch(ParseException | IOException e) {
                                throw new RuntimeException("Failed to load script \"" + entry.getKey() + "\"", e);
                            }
                        })
                        .forEach(structureRegistry::register);
                } catch(IOException e) {
                    throw new RuntimeException("Error occurred while reading config pack files", e);
                }
            })
            .priority(100)
            .failThrough();
    }

    /**
     * Loads every JSON file in the pack with a top level {@code pools} array as a loot table for {@code loot()}, with the
     * file name as its ID.
     */
    private void loadLootTables(ConfigPack pack, CheckedRegistry<LootTable> lootRegistry) {
        Map<String, Path> files;
        try {
            files = FileUtil.filesWithExtension(pack.getRootPath(), ".json");
        } catch(IOException e) {
            throw new RuntimeException("Error occurred while reading config pack files", e);
        }
        files.forEach((path, file) -> {
            try {
                String json = Files.readString(file);
                if(!(new JSONParser().parse(json) instanceof JSONObject object) || !(object.get("pools") instanceof JSONArray)) {
                    return; // some other JSON file
                }
                lootRegistry.register(addon.key(FileUtil.fileName(path)), new LootTableImpl(json, platform));
            } catch(IOException | org.json.simple.parser.ParseException | RuntimeException e) {
                throw new RuntimeException("Failed to load loot table \"" + path + "\"", e);
            }
        });
    }
}
