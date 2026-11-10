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
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.wear.JewelryWear;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nonnull;

/**
 * /mt worn: shows where the player's Jewelry Case is and which pieces count as worn, read
 * fresh from the inventory, and what the plugin last recorded. {@code --refresh=true} also
 * sends the look again.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class WornCommand extends AbstractPlayerCommand {
    private final DefaultArg<Boolean> refreshArg;

    public WornCommand() {
        super("worn", "Show the jewelry you are wearing.");
        this.refreshArg = withDefaultArg("refresh", "Send your look again", ArgTypes.BOOLEAN, false, "false");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        WornJewelry fresh = WornJewelry.of(ref, store);
        WornJewelry recorded = JewelryWear.worn(playerRef.getUuid());
        if (fresh.caseLocation() == null) {
            context.sendMessage(Message.raw("No Jewelry Case in your inventory, so nothing is worn."));
        } else {
            context.sendMessage(Message.raw("Jewelry Case in " + fresh.caseLocation() + ". Worn ("
                    + fresh.pieces().size() + "): " + (fresh.pieces().isEmpty() ? "nothing" : String.join(", ", fresh.itemIds()))));
            for (ItemStack piece : fresh.pieces()) {
                context.sendMessage(Message.raw("  " + piece.getItemId()
                        + (JewelryWear.wornModel(piece, 0) == null ? "" : " (shown on you)")));
            }
        }
        if (!recorded.itemIds().equals(fresh.itemIds())) {
            context.sendMessage(Message.raw("The plugin last recorded: " + recorded.itemIds()
                    + ". It missed a change; run with --refresh=true."));
        }
        if (refreshArg.get(context)) {
            JewelryWear.update(ref, store, true);
            context.sendMessage(Message.raw("Look sent again."));
        }
    }
}
