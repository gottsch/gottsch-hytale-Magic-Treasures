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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /mt give --item=ID: gives the player a new piece of jewelry, with a fresh state (full mana,
 * uses, recharges and repairs) and its tooltip. Unlike vanilla /give, the piece gets its
 * Magic Treasures metadata straight away. With no item, or a wrong one, it lists the
 * jewelry ids that match.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class GiveCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> itemArg;

    public GiveCommand() {
        super("give", "Give yourself a piece of Magic Treasures jewelry.");
        this.itemArg = withDefaultArg("item", "Jewelry item id, e.g. MT_Ring_Gold_Ruby_Great; part of one lists matches",
                ArgTypes.STRING, "", "");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String itemId = itemArg.get(context);
        ItemStack stack = itemId.isEmpty() ? null : Jewelry.create(itemId);
        if (stack == null) {
            String filter = itemId.toLowerCase(Locale.ROOT);
            String ids = JewelryDefinition.getAssetMap().getAssetMap().keySet().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).contains(filter))
                    .sorted()
                    .collect(Collectors.joining(", "));
            context.sendMessage(Message.raw((itemId.isEmpty() ? "" : "No jewelry '" + itemId + "'. ")
                    + "Jewelry: " + (ids.isEmpty() ? "none" : ids)));
            return;
        }
        ItemStackTransaction transaction = Player.giveItem(stack, ref, store);
        ItemStack remainder = transaction.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            context.sendMessage(Message.raw("No room in your inventory for " + itemId + "."));
        } else {
            context.sendMessage(Message.raw("Gave you " + itemId + "."));
        }
    }
}
