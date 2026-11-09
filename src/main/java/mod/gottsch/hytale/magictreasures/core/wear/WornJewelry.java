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

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import org.bson.BsonDocument;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The jewelry a player is wearing: the pieces in their Jewelry Case.
 * <p>
 * The rules (port plan, decision 2):
 * <ul>
 *     <li>The case counts only in the utility section or the hotbar, utility first. If the
 *     player has more than one, only the first found counts.</li>
 *     <li>The case holds 4 pieces, but at most 2 rings, 1 necklace and 1 bracelet are worn,
 *     in slot order. Any other piece in the case is carried, not worn.</li>
 * </ul>
 * The case's contents live in its stack's metadata ({@code Container.Items}), where the
 * vanilla item-container code keeps them, so they are read straight from the stack.
 *
 * @param caseLocation where the case was found, for example {@code utility 0}, or null if
 *                     the player has no case where it counts
 * @param pieces the worn pieces, in case slot order
 * @author Mark Gottschling on 11/09/2026
 */
public record WornJewelry(@Nullable String caseLocation, List<ItemStack> pieces) {
    public static final String CASE_ITEM_ID = "MT_Jewelry_Case";
    public static final WornJewelry NONE = new WornJewelry(null, List.of());

    /** How many pieces of each type can be worn at once. */
    public static final Map<String, Integer> LIMITS = Map.of("Ring", 2, "Necklace", 1, "Bracelet", 1);

    /**
     * Reads what the player is wearing from their utility section and hotbar.
     */
    public static WornJewelry of(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        WornJewelry worn = find(accessor, ref, InventoryComponent.Utility.getComponentType(), "utility");
        if (worn == null) {
            worn = find(accessor, ref, InventoryComponent.Hotbar.getComponentType(), "hotbar");
        }
        return worn == null ? NONE : worn;
    }

    @Nullable
    private static <T extends InventoryComponent> WornJewelry find(ComponentAccessor<EntityStore> accessor,
                                                                    Ref<EntityStore> ref,
                                                                    ComponentType<EntityStore, T> type,
                                                                    String section) {
        T component = accessor.getComponent(ref, type);
        if (component == null) {
            return null;
        }
        ItemContainer container = component.getInventory();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !stack.isEmpty() && CASE_ITEM_ID.equals(stack.getItemId())) {
                return new WornJewelry(section + " " + slot, worn(contents(stack)));
            }
        }
        return null;
    }

    /**
     * @return the stacks inside a Jewelry Case stack, empty slots included as null
     */
    public static ItemStack[] contents(ItemStack caseStack) {
        BsonDocument containerDocument = caseStack.getFromMetadataOrNull(ItemStackItemContainer.CONTAINER_CODEC);
        if (containerDocument == null) {
            return new ItemStack[0];
        }
        ItemStack[] items = ItemStackItemContainer.ITEMS_CODEC.getOrNull(containerDocument, new ExtraInfo());
        return items == null ? new ItemStack[0] : items;
    }

    /**
     * Applies the per-type limits to the case's contents.
     */
    private static List<ItemStack> worn(ItemStack[] contents) {
        Map<String, Integer> counts = new HashMap<>();
        List<ItemStack> pieces = new ArrayList<>();
        for (ItemStack stack : contents) {
            JewelryDefinition definition = Jewelry.definition(stack);
            if (definition == null) {
                continue;
            }
            int count = counts.merge(definition.getType(), 1, Integer::sum);
            if (count <= LIMITS.getOrDefault(definition.getType(), 0)) {
                pieces.add(stack);
            }
        }
        return List.copyOf(pieces);
    }

    /**
     * @return the worn pieces' item ids, which is enough to tell whether the look changed
     */
    public List<String> itemIds() {
        return pieces.stream().map(ItemStack::getItemId).toList();
    }
}
