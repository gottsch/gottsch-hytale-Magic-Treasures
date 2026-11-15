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
import mod.gottsch.hytale.magictreasures.core.cutting.GemCutPage;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCutting;

import javax.annotation.Nonnull;

/**
 * {@code /mt cutting}: shows your gem cutting level and XP. Open to every player (Nov 15,
 * release); setting the level moved to {@link SetCuttingCommand}, for operators.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class CuttingCommand extends AbstractPlayerCommand {

    public CuttingCommand() {
        super("cutting", "Show your gem cutting level.");
        this.setPermissionGroups(MagicTreasuresCommand.PERMISSION_EVERYONE);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        context.sendMessage(Message.raw(describe(GemCutPage.skill(store, ref))));
    }

    /**
     * @return for example "Gem cutting level 3 (50 XP, 40 XP to the next)."
     */
    static String describe(int xp) {
        int now = GemCutting.level(xp);
        String next = now >= GemCutting.MAX_LEVEL ? "max level" : (GemCutting.xpFor(now + 1) - xp) + " XP to the next";
        return "Gem cutting level " + now + " (" + xp + " XP, " + next + ").";
    }
}
