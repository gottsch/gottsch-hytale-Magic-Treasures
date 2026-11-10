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
package mod.gottsch.hytale.magictreasures.core.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * /mt slots: the M4 slot test. Shows the player's ability (rune) slots and lets the server
 * change them, to see whether the client shows and casts what the server puts there.
 * <ul>
 *     <li>no options: list every slot</li>
 *     <li>{@code --put=Rune_Fireball --slot=3}: put a rune in a slot, bypassing the filters
 *     that the Ability Bench applies (core runes go in slots 0, 3, 6..., supports in the
 *     slots after their core)</li>
 *     <li>{@code --clear=3}: empty a slot</li>
 *     <li>{@code --capacity=9}: resize the slots (vanilla is 6: two lines of 3). Runes that
 *     no longer fit are given back to the player.</li>
 * </ul>
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class SlotsCommand extends AbstractPlayerCommand {
    private final OptionalArg<String> putArg;
    private final OptionalArg<Integer> slotArg;
    private final OptionalArg<Integer> clearArg;
    private final OptionalArg<Integer> capacityArg;

    public SlotsCommand() {
        super("slots", "Test: show or change your ability (rune) slots.");
        this.putArg = withOptionalArg("put", "Rune item id to put in --slot", ArgTypes.STRING);
        this.slotArg = withOptionalArg("slot", "Slot for --put", ArgTypes.INTEGER);
        this.clearArg = withOptionalArg("clear", "Slot to empty", ArgTypes.INTEGER);
        this.capacityArg = withOptionalArg("capacity", "New number of slots", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        InventoryComponent.AbilitySlots slots = store.getComponent(ref, InventoryComponent.AbilitySlots.getComponentType());
        if (slots == null) {
            context.sendMessage(Message.raw("You have no ability slots component."));
            return;
        }

        if (capacityArg.provided(context)) {
            int capacity = capacityArg.get(context);
            if (capacity < 1 || capacity > 30) {
                context.sendMessage(Message.raw("Use a capacity from 1 to 30."));
                return;
            }
            List<ItemStack> overflow = new ArrayList<>();
            slots.ensureCapacity((short) capacity, overflow);
            Player player = store.getComponent(ref, Player.getComponentType());
            for (ItemStack stack : overflow) {
                if (player != null && stack != null && !stack.isEmpty()) {
                    player.giveItem(stack, ref, store);
                }
            }
            context.sendMessage(Message.raw("Ability slots resized to " + slots.getInventory().getCapacity()
                    + (overflow.isEmpty() ? "" : "; " + overflow.size() + " rune(s) given back") + "."));
        }

        ItemContainer container = slots.getInventory();
        if (clearArg.provided(context)) {
            int slot = clearArg.get(context);
            if (!inRange(context, container, slot)) {
                return;
            }
            container.removeItemStackFromSlot((short) slot);
            context.sendMessage(Message.raw("Slot " + slot + " emptied."));
        }

        if (putArg.provided(context)) {
            if (!slotArg.provided(context)) {
                context.sendMessage(Message.raw("Give a --slot with --put."));
                return;
            }
            int slot = slotArg.get(context);
            if (!inRange(context, container, slot)) {
                return;
            }
            container.setItemStackForSlot((short) slot, new ItemStack(putArg.get(context), 1));
            context.sendMessage(Message.raw("Put " + putArg.get(context) + " in slot " + slot + "."));
        }

        list(context, container);
    }

    private static boolean inRange(CommandContext context, ItemContainer container, int slot) {
        if (slot < 0 || slot >= container.getCapacity()) {
            context.sendMessage(Message.raw("Slot must be 0 to " + (container.getCapacity() - 1) + "."));
            return false;
        }
        return true;
    }

    private static void list(CommandContext context, ItemContainer container) {
        context.sendMessage(Message.raw("Ability slots: " + container.getCapacity() + " (lines of 3: core, support, support)"));
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            String role = slot % 3 == 0 ? "core" : "support";
            context.sendMessage(Message.raw("  " + slot + " (line " + (slot / 3 + 1) + ", " + role + "): "
                    + (stack == null || stack.isEmpty() ? "empty" : stack.getItemId())));
        }
    }
}
