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

package com.dfsek.terra.mod.implmentation;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;

import java.util.function.Function;

import com.dfsek.terra.api.inventory.Item;
import com.dfsek.terra.api.inventory.ItemStack;
import com.dfsek.terra.mod.CommonPlatform;


/**
 * An item with data (components), e.g. parsed from {@code minecraft:diamond_sword[custom_name="Excalibur"]} or a base64
 * serialized item. Stacks created from it carry that data.
 * <p>
 * Parsing needs the server's registries and item stacks can only be created once a world is loaded, while config packs are
 * loaded before that, so the item is created on first use.
 */
public final class MinecraftItemTemplate implements Item {
    private final String description;
    private final Function<HolderLookup.Provider, net.minecraft.world.item.ItemStack> factory;
    private volatile net.minecraft.world.item.ItemStack prototype;

    private MinecraftItemTemplate(String description, Function<HolderLookup.Provider, net.minecraft.world.item.ItemStack> factory) {
        this.description = description;
        this.factory = factory;
    }

    /**
     * @param data Item ID with data components, in the format of the {@code /give} command
     */
    public static MinecraftItemTemplate fromString(String data) {
        return new MinecraftItemTemplate(data, registries -> {
            try {
                StringReader reader = new StringReader(data);
                ItemInput input = new ItemParser(registries).parse(reader);
                if(reader.canRead()) throw new IllegalArgumentException("Trailing data: " + reader.getRemaining());
                return new net.minecraft.world.item.ItemStack(input.item(), 1, input.components());
            } catch(CommandSyntaxException e) {
                throw new IllegalArgumentException("Invalid item \"" + data + "\": " + e.getMessage(), e);
            }
        });
    }

    /**
     * @param tag Serialized item stack, already upgraded to the current data version
     */
    public static MinecraftItemTemplate fromTag(CompoundTag tag) {
        return new MinecraftItemTemplate(tag.toString(), registries -> net.minecraft.world.item.ItemStack.CODEC
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .getOrThrow(error -> new IllegalArgumentException("Invalid serialized item: " + error)));
    }

    private net.minecraft.world.item.ItemStack prototype() {
        net.minecraft.world.item.ItemStack stack = prototype;
        if(stack == null) {
            synchronized(this) {
                stack = prototype;
                if(stack == null) {
                    MinecraftServer server = CommonPlatform.get().getServer();
                    if(server == null) {
                        throw new IllegalStateException("Item \"" + description + "\" can only be created once a world is loaded");
                    }
                    stack = factory.apply(server.registryAccess());
                    if(stack.isEmpty()) throw new IllegalArgumentException("Item \"" + description + "\" is empty");
                    prototype = stack;
                }
            }
        }
        return stack;
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public ItemStack newItemStack(int amount) {
        return (ItemStack) (Object) prototype().copyWithCount(amount);
    }

    @Override
    public double getMaxDurability() {
        return prototype().getMaxDamage();
    }

    @Override
    public Object getHandle() {
        return prototype().copy();
    }

    @Override
    public String toString() {
        return description;
    }
}
