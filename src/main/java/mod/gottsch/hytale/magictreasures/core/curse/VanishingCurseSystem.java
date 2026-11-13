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
package mod.gottsch.hytale.magictreasures.core.curse;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Curse of Vanishing: when a player dies, every piece of jewelry they carry with that
 * curse ({@link JewelryDefinition#CURSE_VANISHING}) is destroyed, wherever it is in their
 * inventory, inside the Treasures Case too. It runs as the death is recorded, before vanilla
 * drops the player's items ({@code DeathSystems.DropPlayerDeathItems}), so a cursed piece is
 * never dropped for someone else to pick up. It applies in every game mode.
 *
 * @author Mark Gottschling on 11/13/2026
 */
public class VanishingCurseSystem extends DeathSystems.OnDeathSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class));

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent death,
                                 @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        List<ItemStack> gone = new ArrayList<>();
        for (ComponentType<EntityStore, ? extends InventoryComponent> type : InventoryComponent.EVERYTHING) {
            InventoryComponent component = store.getComponent(ref, type);
            if (component != null) {
                vanish(component.getInventory(), gone);
            }
        }
        if (gone.isEmpty()) {
            return;
        }
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        LOGGER.atInfo().log("Curse of Vanishing: %s lost %s", player == null ? ref : player.getUsername(),
                gone.stream().map(ItemStack::getItemId).toList());
        if (player != null) {
            for (ItemStack piece : gone) {
                // the piece's own (translated) name, so a named piece is called by its name
                player.sendMessage(Message.join(Message.raw("Your "), Jewelry.name(piece),
                        Message.raw(" vanished with your death.")).color("#ff7070"));
            }
        }
    }

    /**
     * Removes the cursed pieces from a container, and from any Treasures Case in it.
     *
     * @param gone the pieces removed, added to
     */
    private static void vanish(ItemContainer container, List<ItemStack> gone) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (cursed(stack)) {
                gone.add(stack);
                container.removeItemStackFromSlot(slot);
            } else if (WornJewelry.CASE_ITEM_ID.equals(stack.getItemId())) {
                ItemStackItemContainer inside = ItemStackItemContainer.getContainer(container, slot);
                if (inside != null) {
                    vanish(inside, gone);
                }
            }
        }
    }

    private static boolean cursed(ItemStack stack) {
        JewelryDefinition definition = Jewelry.definition(stack);
        return definition != null && definition.hasCurse(JewelryDefinition.CURSE_VANISHING);
    }
}
