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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCutPage;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCutting;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCuttingSkill;

import javax.annotation.Nonnull;

/**
 * {@code /mt cutting [level]}: shows your gem cutting level and XP, or, for testing, sets
 * your level (to the start of it).
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class CuttingCommand extends AbstractPlayerCommand {
    private final DefaultArg<Integer> levelArg;

    public CuttingCommand() {
        super("cutting", "Show your gem cutting level, or set it for testing.");
        this.levelArg = withDefaultArg("level", "Set your gem cutting level (1-" + GemCutting.MAX_LEVEL
                + "); 0 just shows it", ArgTypes.INTEGER, 0, "0");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        int level = levelArg.get(context);
        if (level > 0) {
            level = Math.min(level, GemCutting.MAX_LEVEL);
            store.putComponent(ref, GemCuttingSkill.getComponentType(),
                    new GemCuttingSkill(GemCutting.xpFor(level)));
        }
        int xp = GemCutPage.skill(store, ref);
        int now = GemCutting.level(xp);
        String next = now >= GemCutting.MAX_LEVEL ? "max level" : (GemCutting.xpFor(now + 1) - xp) + " XP to the next";
        context.sendMessage(Message.raw("Gem cutting level " + now + " (" + xp + " XP, " + next + ")."));
    }
}
