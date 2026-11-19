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
 *     <li>{@link Trigger#TICK}: once a second while worn. Healing heals when hurt; Speed
 *     and Cat Sight start a buff when it isn't already running; a Drain aura starts when a
 *     hostile mob is in range.</li>
 *     <li>{@link Trigger#HIT}: when the wearer hits something directly (not with a status
 *     tick). Vampiric drains its amount from the target and heals by it; a Strike deals flat element
 *     damage to what was hit; Strength and the Weapon spells start
 *     their buff, so it runs during the fight (Nov 12: as tick spells they cast whenever ready,
 *     in a fight or not, and emptied the piece's mana).</li>
 *     <li>{@link Trigger#HURT}: when the wearer takes damage of one of the spell's
 *     {@code Element}s. A Ward (Stoneskin is a Ward of Physical and Projectile) starts its
 *     resistance, so the first hit isn't warded; Thorns hands part of a melee hit back to the
 *     attacker; a Barrier starts when the hit leaves the wearer low on health.</li>
 *     <li>{@link Trigger#BLOCK}: when the wearer blocks a hit by an entity, with a shield or
 *     a weapon's guard ({@link GuardSystem}). A Guard stuns a melee attacker.</li>
 * </ul>
 * A spell asset names its kind in {@code Kind}, for example {@code "Cat_Sight"}.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public enum SpellKind {
    HEALING(Trigger.TICK),
    SPEED(Trigger.TICK),
    STRENGTH(Trigger.HIT),
    CAT_SIGHT(Trigger.TICK),
    /** A buff of elemental resistance (Fire Ward, ...), Nov 11. */
    WARD(Trigger.HURT),
    /** A buff of elemental damage that also gives a status on every hit (Flame Weapon, ...). */
    WEAPON(Trigger.HIT),
    /**
     * Drains the spell's amount from what the wearer hits, as its own hit, and heals them by
     * what it drained (Vampiric Kiss, Bite, Embrace; Nov 13). Was DRAIN until Nov 13; that
     * name now belongs to the aura.
     */
    VAMPIRIC(Trigger.HIT),
    /**
     * Deals a flat amount of an element to what was hit, as its own hit, once per cooldown
     * (Fire Strike, ...). Unlike a Weapon spell it has no buff and no status, and it doesn't
     * scale with the weapon: it helps weak and fast weapons most (Nov 13).
     */
    STRIKE(Trigger.HIT),
    /**
     * An aura that takes health from nearby hostile mobs, without contact, every few seconds
     * while it lasts, and heals the wearer by the total ({@link DrainAura}, Nov 13). Cast only
     * when a hostile mob is in range; it damages even at full health.
     */
    DRAIN(Trigger.TICK),
    /**
     * Hands the spell's {@code Effect} share of a direct hit taken back to the attacker, as the
     * wearer's own hit (Nov 17, 1.2.0). Only hits from an entity, not statuses or bonus hits,
     * so two wearers can't bounce it between them.
     */
    THORNS(Trigger.HURT),
    /**
     * A Ward that starts only when a hit leaves the wearer below
     * {@link SpellEngine#BARRIER_HEALTH} of their health (Nov 17, 1.2.0).
     */
    BARRIER(Trigger.HURT),
    /**
     * While worn with the charge for a cast, takes the spell's {@code Effect} share off the
     * stamina each block costs; a blocked melee hit stuns the attacker ({@code EntityEffect})
     * for its {@code Duration}, once per cooldown (v1.3, {@link GuardSystem}).
     */
    GUARD(Trigger.BLOCK);

    public enum Trigger {
        TICK, HIT, HURT, BLOCK
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
