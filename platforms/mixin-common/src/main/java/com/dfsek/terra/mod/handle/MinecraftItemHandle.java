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

package com.dfsek.terra.mod.handle;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Items;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import com.dfsek.terra.api.inventory.Item;
import com.dfsek.terra.api.inventory.item.Enchantment;
import com.dfsek.terra.handle.AbstractItemHandle;
import com.dfsek.terra.mod.CommonPlatform;
import com.dfsek.terra.mod.implmentation.MinecraftItemTemplate;


public class MinecraftItemHandle extends AbstractItemHandle {

    @Override
    protected Item createItemFromString(String data) {
        int dataStart = data.indexOf('[');
        if(dataStart == -1) dataStart = data.indexOf('{');
        net.minecraft.world.item.Item item = parseItemId(dataStart == -1 ? data : data.substring(0, dataStart));
        if(dataStart == -1) {
            return (Item) item;
        }
        // Components can only be parsed once the server's registries exist.
        return MinecraftItemTemplate.fromString(data);
    }

    private static net.minecraft.world.item.Item parseItemId(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if(identifier == null) throw new IllegalArgumentException("Invalid item ID: " + id);
        return BuiltInRegistries.ITEM.getOptional(identifier)
            .filter(item -> item != Items.AIR)
            .orElseThrow(() -> new IllegalArgumentException("No such item: " + id));
    }

    @Override
    protected Item createItemFromBytes(byte[] bytes) {
        if(isJavaSerialization(bytes)) {
            throw new IllegalArgumentException("Items serialized with BukkitObjectOutputStream are only supported on Bukkit platforms; " +
                                               "use ItemStack#serializeAsBytes instead");
        }
        if(!isGzip(bytes)) {
            throw new IllegalArgumentException("Unknown serialized item format");
        }

        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        } catch(IOException e) {
            throw new IllegalArgumentException("Invalid serialized item", e);
        }

        // Same as Paper's ItemStack#deserializeBytes: upgrade items serialized by older versions.
        int dataVersion = NbtUtils.getDataVersion(tag, 0);
        CompoundTag upgraded = (CompoundTag) DataFixers.getDataFixer()
            .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), dataVersion,
                SharedConstants.getCurrentVersion().dataVersion().version())
            .getValue();
        upgraded.remove("DataVersion");

        parseItemId(upgraded.getStringOr("id", "minecraft:air"));
        return MinecraftItemTemplate.fromTag(upgraded);
    }

    @Override
    public Enchantment getEnchantment(String id) {
        return (Enchantment) (Object) (CommonPlatform.get().enchantmentRegistry().get(Identifier.tryParse(id)));
    }

    @Override
    public Set<Enchantment> getEnchantments() {
        return CommonPlatform.get().enchantmentRegistry().stream().map(enchantment -> (Enchantment) (Object) enchantment).collect(
            Collectors.toSet());
    }
}
