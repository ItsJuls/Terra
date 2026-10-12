/*
 * This file is part of Terra.
 *
 * Terra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Terra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Terra.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.dfsek.terra.handle;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dfsek.terra.api.handle.ItemHandle;
import com.dfsek.terra.api.handle.ItemProvider;
import com.dfsek.terra.api.inventory.Item;


/**
 * Parses the item string formats described in {@link ItemHandle#createItem(String)}, delegating platform specific
 * parsing to subclasses.
 */
public abstract class AbstractItemHandle implements ItemHandle {
    private static final String BASE64_PREFIX = "base64:";
    // Base64 of the gzip magic number (Paper's ItemStack#serializeAsBytes) and the Java serialization magic number.
    private static final String GZIP_BASE64 = "H4sI";
    private static final String JAVA_SERIALIZATION_BASE64 = "rO0";

    private final Map<String, ItemProvider> providers = new ConcurrentHashMap<>();

    @Override
    public Item createItem(String data) {
        String item = data.trim();

        if(item.startsWith(BASE64_PREFIX)) {
            return fromBase64(item.substring(BASE64_PREFIX.length()));
        }
        if(item.startsWith(GZIP_BASE64) || item.startsWith(JAVA_SERIALIZATION_BASE64)) {
            return fromBase64(item);
        }

        int separator = item.indexOf(':');
        if(separator > 0) {
            ItemProvider provider = providers.get(item.substring(0, separator).toLowerCase(Locale.ROOT));
            if(provider != null) {
                Item provided = provider.createItem(item.substring(separator + 1));
                if(provided == null) throw new IllegalArgumentException("No such item: " + item);
                return provided;
            }
        }

        return createItemFromString(item);
    }

    @Override
    public void registerItemProvider(String namespace, ItemProvider provider) {
        String key = namespace.toLowerCase(Locale.ROOT);
        if(key.equals("minecraft") || key.equals("base64")) {
            throw new IllegalArgumentException("Cannot register an item provider for reserved namespace " + namespace);
        }
        if(providers.putIfAbsent(key, provider) != null) {
            throw new IllegalArgumentException("An item provider is already registered for namespace " + namespace);
        }
    }

    private Item fromBase64(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(base64);
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 item data", e);
        }
        return createItemFromBytes(bytes);
    }

    /**
     * Creates an item from an item ID, optionally followed by data components.
     */
    protected abstract Item createItemFromString(String data);

    /**
     * Creates an item from serialized item bytes.
     */
    protected abstract Item createItemFromBytes(byte[] bytes);

    protected static boolean isGzip(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    protected static boolean isJavaSerialization(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0xAC && (bytes[1] & 0xFF) == 0xED;
    }
}
