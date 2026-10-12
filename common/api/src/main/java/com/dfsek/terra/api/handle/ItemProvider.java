/*
 * Copyright (c) 2020-2025 Polyhedral Development
 *
 * The Terra API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the common/api directory.
 */

package com.dfsek.terra.api.handle;

import org.jetbrains.annotations.Nullable;

import com.dfsek.terra.api.inventory.Item;


/**
 * Creates items for a namespace of item strings. See {@link ItemHandle#registerItemProvider(String, ItemProvider)}.
 */
@FunctionalInterface
public interface ItemProvider {
    /**
     * @param data The item string, without the {@code <namespace>:} prefix
     *
     * @return The item, or {@code null} if there is no such item
     */
    @Nullable Item createItem(String data);
}
