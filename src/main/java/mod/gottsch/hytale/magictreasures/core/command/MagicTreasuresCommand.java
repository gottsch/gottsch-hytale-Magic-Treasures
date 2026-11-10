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

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Root /magictreasures (alias /mt) command that holds the plugin's subcommands.
 * The test subcommands need the generated permission node (operators).
 *
 * @author Mark Gottschling on 11/08/2026
 */
public class MagicTreasuresCommand extends AbstractCommandCollection {

    public MagicTreasuresCommand() {
        super("magictreasures", "Magic Treasures root command.");
        this.addAliases("mt");
        this.addSubCommand(new GiveCommand());
        this.addSubCommand(new InfoCommand());
        this.addSubCommand(new CastTestCommand());
        this.addSubCommand(new WornCommand());
        this.addSubCommand(new HitsCommand());
        this.addSubCommand(new SlotsCommand());
    }
}
