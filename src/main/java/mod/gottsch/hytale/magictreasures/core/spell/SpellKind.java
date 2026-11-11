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

import javax.annotation.Nullable;

/**
 * The kinds of passive spell, each with the code that runs it ({@link SpellEngine}) and when
 * it is tried:
 * <ul>
 *     <li>{@link Trigger#TICK}: once a second while worn. Healing heals when hurt; Speed,
 *     Strength and Cat Sight start a buff when it isn't already running.</li>
 *     <li>{@link Trigger#HIT}: when the wearer damages something. Drain heals the wearer by
 *     part of the damage.</li>
 * </ul>
 * A spell asset names its kind in {@code Kind}, for example {@code "Cat_Sight"}.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public enum SpellKind {
    HEALING(Trigger.TICK),
    SPEED(Trigger.TICK),
    STRENGTH(Trigger.TICK),
    CAT_SIGHT(Trigger.TICK),
    /** A buff of elemental resistance (Fire Ward, ...), Nov 11. */
    WARD(Trigger.TICK),
    /** A buff of elemental damage that also gives a status on every hit (Flame Weapon, ...). */
    WEAPON(Trigger.TICK),
    DRAIN(Trigger.HIT);

    public enum Trigger {
        TICK, HIT
    }

    private final Trigger trigger;

    SpellKind(Trigger trigger) {
        this.trigger = trigger;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    /**
     * @return the kind with this name, ignoring case ({@code Cat_Sight} or {@code CatSight}),
     * or null if there is none
     */
    @Nullable
    public static SpellKind of(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String key = name.replace("_", "").replace(" ", "");
        for (SpellKind kind : values()) {
            if (kind.name().replace("_", "").equalsIgnoreCase(key)) {
                return kind;
            }
        }
        return null;
    }
}
