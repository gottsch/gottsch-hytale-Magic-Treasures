/*
 * This file is part of  Magic Treasures.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Magic Treasures is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Magic Treasures is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Magic Treasures.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.hytale.magictreasures.core.wear;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemStackContainerConfig;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Opens the {@link JewelryCasePage} when the Treasures Case is right-clicked, from the hand or the
 * utility slot: {@code "Page": {"Id": "MT_JewelryCase"}} in the case's item JSON. Like vanilla's
 * {@code OpenItemStackContainer}, it first makes sure the case has its container, then moves any
 * piece sitting in another type's slot to its own.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class JewelryCasePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    /** The page id used in item JSON. */
    public static final String ID = "MT_JewelryCase";

    @Nullable
    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor,
                                  @Nonnull PlayerRef playerRef, @Nonnull InteractionContext context) {
        ItemContainer section = context.getHeldItemContainer();
        ItemStack held = context.getHeldItem();
        if (section == null || ItemStack.isEmpty(held) || !WornJewelry.CASE_ITEM_ID.equals(held.getItemId())) {
            return null;
        }
        short slot = context.getHeldItemSlot();
        ItemStackContainerConfig config = held.getItem().getItemStackContainerConfig();
        if (config != null && ItemStackItemContainer.ensureConfiguredContainer(section, slot, config) == null) {
            return null;
        }
        JewelryCasePage page = new JewelryCasePage(playerRef, section, slot);
        page.tidy();
        return page;
    }
}
