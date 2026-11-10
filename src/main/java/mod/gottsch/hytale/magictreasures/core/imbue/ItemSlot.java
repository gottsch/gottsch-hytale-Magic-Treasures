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

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nullable;

/**
 * Where an item is in a player's inventory: a slot of one section, or a slot inside a
 * Jewelry Case that sits in that slot. The stack is read again on every call, because the
 * player can move things while a page is open, and a case's inner container must be fetched
 * again for each write.
 *
 * @param container the inventory section (hotbar, utility, storage or backpack)
 * @param slot      the slot in that section
 * @param innerSlot the slot inside the Jewelry Case at {@code slot}, or -1 for the section's
 *                  own slot
 * @author Mark Gottschling on 11/10/2026
 */
public record ItemSlot(ItemContainer container, short slot, short innerSlot) {

    public static ItemSlot of(ItemContainer container, short slot) {
        return new ItemSlot(container, slot, (short) -1);
    }

    public boolean inCase() {
        return innerSlot >= 0;
    }

    /**
     * @return the stack there now, or null if the slot is empty or the case is gone
     */
    @Nullable
    public ItemStack get() {
        if (!inCase()) {
            return container.getItemStack(slot);
        }
        ItemContainer inner = caseContainer();
        return inner == null ? null : inner.getItemStack(innerSlot);
    }

    /**
     * Puts a stack there, replacing what is there.
     *
     * @return false if it couldn't be written
     */
    public boolean set(ItemStack stack) {
        ItemContainer target = inCase() ? caseContainer() : container;
        if (target == null) {
            return false;
        }
        ItemStackSlotTransaction transaction = target.setItemStackForSlot(inCase() ? innerSlot : slot, stack);
        return transaction.succeeded();
    }

    /**
     * Takes one item from the stack there.
     *
     * @return false if it couldn't be taken
     */
    public boolean takeOne() {
        ItemContainer target = inCase() ? caseContainer() : container;
        if (target == null) {
            return false;
        }
        return target.removeItemStackFromSlot(inCase() ? innerSlot : slot, 1).succeeded();
    }

    /**
     * @return true if both point at the same section slot (a case and a piece in it count as
     * the same slot)
     */
    public boolean sameSlot(ItemSlot other) {
        return container == other.container && slot == other.slot;
    }

    @Nullable
    private ItemContainer caseContainer() {
        ItemStack caseStack = container.getItemStack(slot);
        if (caseStack == null || !WornJewelry.CASE_ITEM_ID.equals(caseStack.getItemId())) {
            return null;
        }
        return ItemStackItemContainer.getContainer(container, slot);
    }
}
