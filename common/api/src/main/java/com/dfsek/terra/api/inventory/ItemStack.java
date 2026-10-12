/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the common/api directory.
 */

package com.dfsek.terra.api.inventory;

import com.dfsek.terra.api.Handle;
import com.dfsek.terra.api.inventory.item.Damageable;
import com.dfsek.terra.api.inventory.item.ItemMeta;


public interface ItemStack extends Handle {
    int getAmount();

    void setAmount(int i);

    Item getType();

    ItemMeta getItemMeta();

    void setItemMeta(ItemMeta meta);

    default boolean isDamageable() {
        return getItemMeta() instanceof Damageable;
    }

    /**
     * Whether this stack can stack with another, i.e. it is the same item with the same data, ignoring the amount.
     *
     * @param other Other stack
     *
     * @return Whether the stacks are similar
     */
    default boolean isSimilar(ItemStack other) {
        return getType().equals(other.getType());
    }
}
