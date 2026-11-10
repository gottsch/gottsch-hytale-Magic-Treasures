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
package mod.gottsch.hytale.magictreasures.core.imbue;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Opens the {@link ImbuePage} from an item's {@code OpenCustomUI} interaction, like vanilla's
 * repair kit: {@code "Page": {"Id": "MT_Imbue"}}. A held spell scroll lists the jewelry; a
 * held piece of jewelry lists what works on it.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class ImbuePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {
    /** The page id used in item JSON. */
    public static final String ID = "MT_Imbue";

    @Nullable
    @Override
    public CustomUIPage tryCreate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor,
                                  @Nonnull PlayerRef playerRef, @Nonnull InteractionContext context) {
        ItemContainer container = context.getHeldItemContainer();
        ItemStack held = context.getHeldItem();
        if (container == null || ItemStack.isEmpty(held)) {
            return null;
        }
        ItemSlot slot = ItemSlot.of(container, (short) context.getHeldItemSlot());
        if (Jewelry.isJewelry(held)) {
            return ImbuePage.forPiece(playerRef, accessor, ref, slot);
        }
        if (Imbuing.kindOf(held) == Imbuing.Kind.SPELL) {
            return ImbuePage.forScroll(playerRef, accessor, ref, slot);
        }
        return null;
    }
}
