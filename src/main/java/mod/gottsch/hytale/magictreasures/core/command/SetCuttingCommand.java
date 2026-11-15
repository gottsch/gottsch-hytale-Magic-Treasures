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
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCutting;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCuttingSkill;

import javax.annotation.Nonnull;

/**
 * {@code /mt setcutting <level>}: sets your gem cutting level, to the start of it, for testing.
 * Split from {@link CuttingCommand} when that opened to players (Nov 15). It has no
 * permission group, so it needs its generated permission node (operators).
 *
 * @author Mark Gottschling on 11/15/2026
 */
public class SetCuttingCommand extends AbstractPlayerCommand {
    private final RequiredArg<Integer> levelArg;

    public SetCuttingCommand() {
        super("setcutting", "Set your gem cutting level (testing).");
        this.levelArg = withRequiredArg("level", "Gem cutting level, 1-" + GemCutting.MAX_LEVEL, ArgTypes.INTEGER);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        int level = Math.max(1, Math.min(levelArg.get(context), GemCutting.MAX_LEVEL));
        int xp = GemCutting.xpFor(level);
        store.putComponent(ref, GemCuttingSkill.getComponentType(), new GemCuttingSkill(xp));
        context.sendMessage(Message.raw(CuttingCommand.describe(xp)));
    }
}
