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

import mod.gottsch.hytale.magictreasures.core.cutting.GemCut;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A spell as a piece's tooltip shows it: what it does on this piece, with the numbers the
 * piece casts it with, the way Torchlight shows both the total and its parts (Mark, Nov 14:
 * "if the piece says +25%, it should do +25%").
 * <ul>
 *     <li>{@link #summary}: the total amount, for example {@code drains 25 on hit}.</li>
 *     <li>{@link #breakdown}: where the amount comes from, the spell's base then each
 *     factor that changes it, for example {@code 20, +25% Bone}. Factors multiply, so the
 *     parts are multipliers, not addends.</li>
 *     <li>{@link #details}: up to two lines, as totals only: what the spell does besides
 *     its amount (targets, status, duration), then its cost and cooldown. Two lines, as one
 *     wrapped mid-way on a Drain aura (Nov 15).</li>
 * </ul>
 * The numbers match {@link SpellEngine}: the named piece's {@code SpellOverrides} first, then
 * the piece's factors (material x gem, the gem's scaled by its cut).
 *
 * @author Mark Gottschling on 11/14/2026
 */
public record SpellTooltip(String name, String summary, String breakdown, List<String> details) {

    /**
     * @param cut the piece's gem cut, or null for Fine
     * @return the tooltip text of this spell on the piece, or null if the spell isn't known
     */
    @Nullable
    public static SpellTooltip of(String spellId, JewelryStats stats, @Nullable GemCut cut) {
        Spell base = Spell.get(spellId);
        if (base == null || base.getKind() == null) {
            return null;
        }
        JewelryDefinition definition = stats.definition();
        Spell spell = definition == null ? base : base.withOverrides(definition.getSpellOverrides());
        SpellFactors factors = stats.factors();
        double effect = spell.getEffect() * factors.getEffect();
        String name = spell.getName() + " (" + spell.getLevel() + ")";

        String summary;
        String baseText = number(spell.getEffect());
        List<String> extras = new ArrayList<>();
        switch (spell.getKind()) {
            case HEALING -> summary = "heals " + number(effect);
            case VAMPIRIC -> summary = "drains " + number(effect) + " on hit";
            case STRIKE -> summary = number(effect) + " " + element(spell.getElement()) + " damage on hit";
            case DRAIN -> {
                summary = "drains " + number(effect) + " per mob every " + rounded(spell.getPulse()) + " s";
                extras.add("up to " + spell.getMaxTargets() + " mobs within "
                        + number(spell.getRadius() * factors.getRange()) + " m");
            }
            case WEAPON -> {
                summary = "+" + Math.round(effect * 100) + "% " + element(spell.getElement()) + " per hit";
                baseText = Math.round(spell.getEffect() * 100) + "%";
                if (spell.getOnHit() != null) {
                    String status = status(spell.getOnHit());
                    extras.add(spell.getOnHitChance() >= 1 ? status + " on hit"
                            : status + " on " + number(spell.getOnHitChance() * 100) + "% of hits");
                }
            }
            case WARD -> summary = "wards " + String.join(", ", spell.getElements().stream()
                    .map(SpellTooltip::element).toList());
            default -> summary = "";
        }

        // only spells with an amount get a breakdown
        String breakdown = "";
        if (usesEffect(spell.getKind())) {
            List<String> parts = new ArrayList<>();
            parts.add(baseText);
            if (stats.material() != null) {
                part(parts, stats.material().getFactors().getEffect(), stats.material().getId());
            }
            if (stats.gem() != null) {
                GemCut gemCut = cut == null ? GemCut.FINE : cut;
                part(parts, gemCut.scale(stats.gem().getFactors()).getEffect(),
                        stats.gem().getId().toLowerCase(Locale.ROOT));
            }
            // just the base: nothing to break down
            if (parts.size() > 1) {
                breakdown = String.join(", ", parts);
            }
        }

        if (spell.getDuration() > 0) {
            extras.add("lasts " + rounded(spell.getDuration() * factors.getDuration()) + " s");
        }
        List<String> costs = new ArrayList<>();
        if (definition == null || !definition.isUnlimited()) {
            costs.add("cost " + rounded(spell.getCost() * factors.getCost()));
        }
        costs.add("cooldown " + rounded(spell.getCooldown() * factors.getCooldown()) + " s");
        List<String> details = new ArrayList<>();
        if (!extras.isEmpty()) {
            details.add(String.join(", ", extras));
        }
        details.add(String.join(", ", costs));
        return new SpellTooltip(name, summary, breakdown, details);
    }

    /**
     * @return true if the kind's {@code Effect} is an amount the piece scales (buffs such as
     * Speed take theirs from the entity effect asset)
     */
    private static boolean usesEffect(SpellKind kind) {
        return switch (kind) {
            case HEALING, VAMPIRIC, STRIKE, DRAIN, WEAPON -> true;
            default -> false;
        };
    }

    /**
     * Adds a factor's part, for example {@code +25% Bone}, unless it rounds to no change.
     */
    private static void part(List<String> parts, double factor, String source) {
        long percent = Math.round((factor - 1) * 100);
        if (percent != 0) {
            parts.add((percent > 0 ? "+" : "") + percent + "% " + source);
        }
    }

    /**
     * A time or a cost as a player reads it (Mark, Nov 15: "8.9 is meaningless to someone"):
     * to the nearest half under 60 (8.9 as 9, 8.4 as 8.5), to the nearest whole from 60 up
     * (110.6 as 111).
     */
    static String rounded(double value) {
        return value < 60 ? number(Math.round(value * 2) / 2.0) : Long.toString(Math.round(value));
    }

    /**
     * @return a whole number without decimals, otherwise one decimal (12.5, 31.3)
     */
    static String number(double value) {
        if (Math.abs(value - Math.round(value)) < 0.05) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String element(@Nullable String element) {
        return element == null ? "elemental" : element.toLowerCase(Locale.ROOT);
    }

    /**
     * @return a status effect id as a name, for example {@code MT_Status_Stun} as {@code Stun}
     */
    private static String status(String id) {
        return id.replaceFirst("^MT_Status_", "").replace('_', ' ');
    }
}
