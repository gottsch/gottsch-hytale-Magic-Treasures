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
package mod.gottsch.hytale.magictreasures.core.element;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The source of an element bonus hit: the extra damage of one element that a player's hit adds
 * ("+10% as Fire damage", a weapon spell's share). It is the player's own hit, so resistances,
 * weaknesses and kill credit work as for any hit; the type tells the systems not to treat it as
 * a new weapon hit, so it never crits, gives statuses, adds bonuses of its own or runs hit
 * spells.
 *
 * @author Mark Gottschling on 11/12/2026
 */
public class ElementBonusSource extends Damage.EntitySource {

    public ElementBonusSource(Ref<EntityStore> attacker) {
        super(attacker);
    }

    /**
     * @return true if the damage is an element bonus hit
     */
    public static boolean isBonus(Damage damage) {
        return damage.getSource() instanceof ElementBonusSource;
    }
}
