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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.loot.LootRoller;
import mod.gottsch.hytale.magictreasures.core.loot.LootTable;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /mt loot --droplist=NAME [--source=Chest|Mob] [--chance=true]: shows which of our loot tables
 * a vanilla drop list gets, and rolls it into the player's inventory, for testing without
 * finding fresh chests. The table's chance is skipped unless {@code --chance=true}.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class LootCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> dropListArg;
    private final DefaultArg<String> sourceArg;
    private final DefaultArg<Boolean> chanceArg;

    public LootCommand() {
        super("loot", "Roll our loot for a vanilla drop list, e.g. Zone2_Undead_Tier3.");
        this.dropListArg = withDefaultArg("droplist", "Vanilla drop list id", ArgTypes.STRING, "", "");
        this.sourceArg = withDefaultArg("source", "Chest or Mob", ArgTypes.STRING, LootTable.CHEST, LootTable.CHEST);
        this.chanceArg = withDefaultArg("chance", "Apply the table's chance", ArgTypes.BOOLEAN, false, "false");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String dropList = dropListArg.get(context);
        String source = sourceArg.get(context);
        if (dropList.isEmpty()) {
            context.sendMessage(Message.raw("Loot tables: " + String.join(", ",
                    new java.util.TreeSet<>(LootTable.getAssetMap().getAssetMap().keySet()))));
            return;
        }
        LootTable table = LootTable.forDropList(source, dropList);
        if (table == null) {
            context.sendMessage(Message.raw("No " + source + " loot table matches " + dropList + "."));
            return;
        }
        List<ItemStack> loot = chanceArg.get(context)
                ? LootRoller.roll(table, ThreadLocalRandom.current())
                : LootRoller.rollEntries(table, ThreadLocalRandom.current());
        context.sendMessage(Message.raw(dropList + " -> " + table.getId() + String.format(" (chance %.0f%%): ",
                table.getChance() * 100) + (loot.isEmpty() ? "nothing" : String.join(", ",
                loot.stream().map(s -> s.getQuantity() + " " + s.getItemId()).toList()))));
        for (ItemStack stack : loot) {
            Player.giveItem(stack, ref, store);
        }
    }
}
