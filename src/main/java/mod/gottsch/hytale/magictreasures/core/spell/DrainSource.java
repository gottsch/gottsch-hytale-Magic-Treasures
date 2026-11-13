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
package mod.gottsch.hytale.magictreasures.core.spell;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.element.ElementBonusSource;

/**
 * The source of drained health: a Drain aura's pulse ({@link DrainAura}) and, since Nov 13, a
 * Vampiric spell's drain on a hit ({@code SpellEngine}). It is the player's, so kill credit
 * works, but it isn't a weapon hit: like an element bonus hit (which it extends for that
 * reason) it never crits, gives statuses, adds element bonuses or starts hit spells.
 *
 * @author Mark Gottschling on 11/13/2026
 */
public class DrainSource extends ElementBonusSource {

    public DrainSource(Ref<EntityStore> player) {
        super(player);
    }
}
