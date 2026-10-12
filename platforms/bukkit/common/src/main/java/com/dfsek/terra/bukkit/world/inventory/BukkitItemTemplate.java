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

package com.dfsek.terra.bukkit.world.inventory;

import com.dfsek.terra.api.inventory.Item;
import com.dfsek.terra.api.inventory.ItemStack;


/**
 * An item with data (components), e.g. parsed from {@code minecraft:diamond_sword[custom_name="Excalibur"]} or a base64
 * serialized item. Stacks created from it carry that data.
 */
public class BukkitItemTemplate implements Item {
    private final org.bukkit.inventory.ItemStack prototype;

    public BukkitItemTemplate(org.bukkit.inventory.ItemStack prototype) {
        if(prototype.isEmpty()) throw new IllegalArgumentException("Item is empty");
        this.prototype = prototype.clone();
    }

    @Override
    public ItemStack newItemStack(int amount) {
        org.bukkit.inventory.ItemStack stack = prototype.clone();
        stack.setAmount(amount);
        return new BukkitItemStack(stack);
    }

    @Override
    public double getMaxDurability() {
        return prototype.getType().getMaxDurability();
    }

    @Override
    public org.bukkit.inventory.ItemStack getHandle() {
        return prototype.clone();
    }
}
