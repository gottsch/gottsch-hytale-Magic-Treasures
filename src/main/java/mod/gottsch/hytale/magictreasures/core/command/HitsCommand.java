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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.spell.HitMeter;

import javax.annotation.Nonnull;

/**
 * /mt hits: turns on or off a chat line for each hit the player deals, split into its base
 * damage, the bonus from effects, elements and crits, and the damage after armor
 * ({@link HitMeter}), so buffs such as Strength can be checked. A crit adds "CRIT x1.5".
 * <p>
 * For testing, {@code --crit=true} makes every weapon hit crit (and turns the display on), and
 * {@code --crit=false} goes back to the worn crit chance.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class HitsCommand extends AbstractPlayerCommand {
    private final OptionalArg<Boolean> critArg;

    public HitsCommand() {
        super("hits", "Show the damage of each hit you deal (toggle).");
        this.critArg = withOptionalArg("crit", "Test: true makes every weapon hit crit, false stops it",
                ArgTypes.BOOLEAN);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (critArg.provided(context)) {
            if (critArg.get(context)) {
                HitMeter.FORCE_CRITS.add(playerRef.getUuid());
                HitMeter.SHOW_HITS.add(playerRef.getUuid());
                context.sendMessage(Message.raw("Every weapon hit now crits, and the hit display is on. "
                        + "/mt hits --crit=false to stop."));
            } else {
                HitMeter.FORCE_CRITS.remove(playerRef.getUuid());
                context.sendMessage(Message.raw("Crits are back to your worn crit chance."));
            }
            return;
        }
        if (HitMeter.SHOW_HITS.remove(playerRef.getUuid())) {
            context.sendMessage(Message.raw("Hit damage display off."));
        } else {
            HitMeter.SHOW_HITS.add(playerRef.getUuid());
            context.sendMessage(Message.raw("Hit damage display on. Run /mt hits again to turn it off."));
        }
    }
}
