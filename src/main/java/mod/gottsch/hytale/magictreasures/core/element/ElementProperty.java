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

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * An elemental property a piece of jewelry rolled, saved in its state
 * ({@code JewelryState.Elements}) as text: {@code Kind:Element:Value[:Effect]}, for example
 * {@code Resistance:Fire:0.12} or {@code OnHit:Fire:0.10:Burn}.
 *
 * @param kind    resistance to, or extra damage of, the element, or a status given on hit
 * @param element a damage cause id, or {@code Elemental} for all elements ({@code Any} for
 *                {@link Kind#CRIT})
 * @param value   a share of damage (0.12 is 12%, negative for a weakness), or for
 *                {@link Kind#ON_HIT} the chance per hit
 * @param effect  the status effect given on hit (vanilla {@code Burn}, {@code Poison},
 *                {@code Slow}, ...), or null
 * @author Mark Gottschling on 11/11/2026
 */
public record ElementProperty(Kind kind, String element, double value, @Nullable String effect) {

    public enum Kind {
        /** Less damage taken of the element (negative: more). */
        RESISTANCE,
        /** More damage dealt of the element. */
        DAMAGE,
        /** A chance per melee or ranged hit to give the target a status. */
        ON_HIT,
        /** A chance per melee or ranged hit to deal critical damage (Nov 11; Hytale has none). */
        CRIT;

        @Nullable
        public static Kind of(@Nullable String name) {
            if (name == null) {
                return null;
            }
            String key = name.replace("_", "");
            for (Kind kind : values()) {
                if (kind.name().replace("_", "").equalsIgnoreCase(key)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public String encode() {
        String text = kind.name() + ":" + element + ":" + String.format(Locale.ROOT, "%.3f", value);
        return effect == null ? text : text + ":" + effect;
    }

    /**
     * @return the property, or null if the text is damaged
     */
    @Nullable
    public static ElementProperty decode(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String[] bits = text.split(":");
        if (bits.length < 3) {
            return null;
        }
        Kind kind = Kind.of(bits[0]);
        if (kind == null) {
            return null;
        }
        try {
            return new ElementProperty(kind, bits[1], Double.parseDouble(bits[2]), bits.length > 3 ? bits[3] : null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return true if this helps the wearer (a weakness doesn't)
     */
    public boolean good() {
        return value >= 0;
    }

    /**
     * @return the tooltip line, for example {@code Fire resistance +12%},
     * {@code Ice resistance -8%}, {@code +10% as Fire damage} (each weapon hit adds 10% of
     * itself as a Fire hit, Nov 12) or {@code Burn on hit 10%}
     */
    public String describe() {
        String name = "Elemental".equalsIgnoreCase(element) ? "Elemental" : element;
        long percent = Math.round(value * 100);
        return switch (kind) {
            case RESISTANCE -> name + " resistance " + (percent >= 0 ? "+" : "") + percent + "%";
            case DAMAGE -> (percent >= 0 ? "+" : "") + percent + "% as " + name + " damage";
            case ON_HIT -> (effect == null ? name : effect.replace('_', ' ')) + " on hit " + percent + "%";
            case CRIT -> "Critical hit chance " + (percent >= 0 ? "+" : "") + percent + "%";
        };
    }
}
