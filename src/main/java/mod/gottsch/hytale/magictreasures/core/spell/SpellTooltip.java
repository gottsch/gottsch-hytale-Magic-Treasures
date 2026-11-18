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

import com.hypixel.hytale.server.core.Message;
import mod.gottsch.hytale.magictreasures.core.config.MagicTreasuresConfig;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCut;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;
import mod.gottsch.hytale.magictreasures.core.lang.Lang;
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
 *     factor that changes it, for example {@code 20, +25% bone}. Factors multiply, so the
 *     parts are multipliers, not addends.</li>
 *     <li>{@link #details}: up to two lines, as totals only: what the spell does besides
 *     its amount (targets, status, duration), then its cost and cooldown. Two lines, as one
 *     wrapped mid-way on a Drain aura (Nov 15).</li>
 * </ul>
 * The numbers match {@link SpellEngine}: the named piece's {@code SpellOverrides} first, then
 * the piece's factors (material x gem, the gem's scaled by its cut). The words are translation
 * keys, {@code magictreasures.spell.*} in server.lang (v1.2).
 *
 * @author Mark Gottschling on 11/14/2026
 */
public record SpellTooltip(Message name, @Nullable Message summary, @Nullable Message breakdown,
                           List<Message> details) {

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
        Message name = Message.join(Lang.spell(spellId), Message.raw(" (" + spell.getLevel() + ")"));

        Message summary;
        String baseText = number(spell.getEffect());
        List<Message> extras = new ArrayList<>();
        switch (spell.getKind()) {
            case HEALING -> summary = Lang.of("spell.heals").param("amount", number(effect));
            case VAMPIRIC -> summary = Lang.of("spell.drainsOnHit").param("amount", number(effect));
            case STRIKE -> summary = Lang.of("spell.strike").param("amount", number(effect))
                    .param("element", element(spell.getElement()));
            case DRAIN -> {
                summary = Lang.of("spell.drainAura").param("amount", number(effect))
                        .param("pulse", rounded(spell.getPulse()));
                extras.add(Lang.of("spell.drainTargets").param("count", spell.getMaxTargets())
                        .param("range", number(spell.getRadius() * factors.getRange())));
            }
            case WEAPON -> {
                summary = Lang.of("spell.weapon").param("percent", Math.round(effect * 100))
                        .param("element", element(spell.getElement()));
                baseText = Math.round(spell.getEffect() * 100) + "%";
                if (spell.getOnHit() != null) {
                    Message status = Lang.status(spell.getOnHit());
                    extras.add(spell.getOnHitChance() >= 1
                            ? Lang.of("spell.onHit").param("status", status)
                            : Lang.of("spell.onHitChance").param("status", status)
                                    .param("chance", number(spell.getOnHitChance() * 100)));
                }
            }
            case WARD -> summary = Lang.of("spell.wards").param("elements", elements(spell));
            case THORNS -> {
                summary = Lang.of("spell.thorns").param("percent", Math.round(effect * 100));
                baseText = Math.round(spell.getEffect() * 100) + "%";
            }
            case BARRIER -> {
                summary = Lang.of("spell.wards").param("elements", elements(spell));
                extras.add(Lang.of("spell.barrierWhen")
                        .param("percent", Math.round(SpellEngine.BARRIER_HEALTH * 100)));
            }
            default -> summary = null;
        }

        // only spells with an amount get a breakdown
        Message breakdown = null;
        if (usesEffect(spell.getKind())) {
            List<Message> parts = new ArrayList<>();
            parts.add(Message.raw(baseText));
            if (stats.material() != null) {
                part(parts, stats.material().getFactors().getEffect(), stats.material().getId());
            }
            if (stats.gem() != null) {
                GemCut gemCut = cut == null ? GemCut.FINE : cut;
                part(parts, gemCut.scale(stats.gem().getFactors()).getEffect(), stats.gem().getId());
            }
            // just the base: nothing to break down
            if (parts.size() > 1) {
                breakdown = Lang.join(parts, ", ");
            }
        }

        if (spell.getDuration() > 0) {
            extras.add(Lang.of("spell.lasts").param("seconds",
                    rounded(spell.getDuration() * factors.getDuration())));
        }
        List<Message> costs = new ArrayList<>();
        if (definition == null || !definition.isUnlimited()) {
            costs.add(Lang.of("spell.cost").param("cost", rounded(spell.getCost() * factors.getCost())));
        }
        costs.add(Lang.of("spell.cooldown").param("seconds", rounded(spell.getCooldown()
                * factors.getCooldown() * MagicTreasuresConfig.get().getSpellCooldowns())));
        List<Message> details = new ArrayList<>();
        if (!extras.isEmpty()) {
            details.add(Lang.join(extras, ", "));
        }
        details.add(Lang.join(costs, ", "));
        return new SpellTooltip(name, summary, breakdown, details);
    }

    /** @return a Ward's elements, "fire, poison" */
    private static Message elements(Spell spell) {
        return Lang.join(spell.getElements().stream().map(SpellTooltip::element).toList(), ", ");
    }

    /**
     * @return true if the kind's {@code Effect} is an amount the piece scales (buffs such as
     * Speed take theirs from the entity effect asset)
     */
    private static boolean usesEffect(SpellKind kind) {
        return switch (kind) {
            case HEALING, VAMPIRIC, STRIKE, DRAIN, WEAPON, THORNS -> true;
            default -> false;
        };
    }

    /**
     * Adds a factor's part, for example {@code +25% bone}, unless it rounds to no change.
     */
    private static void part(List<Message> parts, double factor, String source) {
        long percent = Math.round((factor - 1) * 100);
        if (percent != 0) {
            parts.add(Message.join(Message.raw((percent > 0 ? "+" : "") + percent + "% "),
                    Lang.source(source)));
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

    /** @return an element inside a sentence ("fire"), "elemental" if none */
    private static Message element(@Nullable String element) {
        return Lang.elementLower(element == null ? "Elemental" : element);
    }
}
