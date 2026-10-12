/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the common/api directory.
 */

package com.dfsek.terra.api.handle;

import java.util.Set;

import com.dfsek.terra.api.inventory.Item;
import com.dfsek.terra.api.inventory.item.Enchantment;


public interface ItemHandle {

    /**
     * Creates an item from a string. Supported formats:
     * <ul>
     *     <li>An item ID, e.g. {@code minecraft:diamond}</li>
     *     <li>An item ID with data components, in the same format as the {@code /give} command, e.g.
     *     {@code minecraft:diamond_sword[custom_name="Excalibur",enchantments={sharpness:5}]}</li>
     *     <li>A base64 encoded item, optionally prefixed with {@code base64:}. Both Paper's
     *     {@code ItemStack#serializeAsBytes} format and (on Bukkit platforms) {@code BukkitObjectOutputStream} are supported.
     *     Items from older Minecraft versions are upgraded.</li>
     *     <li>{@code <namespace>:<data>} for a namespace registered with {@link #registerItemProvider(String, ItemProvider)}</li>
     * </ul>
     *
     * Items created from data produce item stacks carrying that data.
     *
     * @param data Item string
     *
     * @return The item
     *
     * @throws IllegalArgumentException if the item cannot be created
     */
    Item createItem(String data);

    Enchantment getEnchantment(String id);

    Set<Enchantment> getEnchantments();

    /**
     * Registers a provider for item strings in the given namespace, e.g. items from another plugin or mod.
     * {@link #createItem(String)} passes everything after {@code <namespace>:} to the provider.
     *
     * @param namespace Namespace handled by the provider
     * @param provider  The provider
     *
     * @throws UnsupportedOperationException if this platform does not support item providers
     */
    default void registerItemProvider(String namespace, ItemProvider provider) {
        throw new UnsupportedOperationException("Item providers are not supported on this platform");
    }
}
