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

package com.dfsek.terra.bukkit.handles;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OptionalDataException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.dfsek.terra.api.inventory.Item;
import com.dfsek.terra.api.inventory.item.Enchantment;
import com.dfsek.terra.bukkit.util.MinecraftUtils;
import com.dfsek.terra.bukkit.world.BukkitAdapter;
import com.dfsek.terra.bukkit.world.inventory.BukkitItemTemplate;
import com.dfsek.terra.handle.AbstractItemHandle;


public class BukkitItemHandle extends AbstractItemHandle {

    @Override
    protected Item createItemFromString(String data) {
        if(data.indexOf('[') != -1 || data.indexOf('{') != -1) {
            // Same format as the /give command, including data components.
            return new BukkitItemTemplate(Bukkit.getItemFactory().createItemStack(data));
        }
        Material material = Material.matchMaterial(data);
        if(material == null || !material.isItem()) throw new IllegalArgumentException("No such item: " + data);
        return BukkitAdapter.adapt(material);
    }

    @Override
    protected Item createItemFromBytes(byte[] bytes) {
        if(isGzip(bytes)) {
            // ItemStack#serializeAsBytes, upgraded to the current data version by Paper.
            return new BukkitItemTemplate(org.bukkit.inventory.ItemStack.deserializeBytes(bytes));
        }
        if(isJavaSerialization(bytes)) {
            return new BukkitItemTemplate(readBukkitObjectStream(bytes));
        }
        throw new IllegalArgumentException("Unknown serialized item format");
    }

    /**
     * Reads an item written with {@link BukkitObjectOutputStream}, either directly or as an array of items (optionally
     * preceded by its length).
     */
    @SuppressWarnings("deprecation")
    private static org.bukkit.inventory.ItemStack readBukkitObjectStream(byte[] bytes) {
        try(BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object;
            try {
                object = in.readObject();
            } catch(OptionalDataException e) { // array length written with writeInt
                in.readInt();
                object = in.readObject();
            }
            if(object instanceof org.bukkit.inventory.ItemStack[] items) {
                object = Arrays.stream(items).filter(Objects::nonNull).findFirst().orElse(null);
            }
            if(object instanceof org.bukkit.inventory.ItemStack item) {
                return item;
            }
            throw new IllegalArgumentException("Serialized data is not an item: " + object);
        } catch(IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Invalid serialized item", e);
        }
    }

    @Override
    public Enchantment getEnchantment(String id) {
        return BukkitAdapter.adapt(
            org.bukkit.enchantments.Enchantment.getByKey(NamespacedKey.minecraft(MinecraftUtils.stripMinecraftNamespace(id))));
    }

    @Override
    public Set<Enchantment> getEnchantments() {
        return Arrays.stream(org.bukkit.enchantments.Enchantment.values()).map(BukkitAdapter::adapt).collect(Collectors.toSet());
    }
}
