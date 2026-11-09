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
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.spell.HitMeter;

import javax.annotation.Nonnull;

/**
 * /mt hits: turns on or off a chat line for each hit the player deals, split into its base
 * damage, the bonus from effects, and the damage after armor ({@link HitMeter}), so buffs
 * such as Strength can be checked.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class HitsCommand extends AbstractPlayerCommand {

    public HitsCommand() {
        super("hits", "Show the damage of each hit you deal (toggle).");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (HitMeter.SHOW_HITS.remove(playerRef.getUuid())) {
            context.sendMessage(Message.raw("Hit damage display off."));
        } else {
            HitMeter.SHOW_HITS.add(playerRef.getUuid());
            context.sendMessage(Message.raw("Hit damage display on. Run /mt hits again to turn it off."));
        }
    }
}
