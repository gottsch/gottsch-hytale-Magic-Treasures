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
package mod.gottsch.hytale.magictreasures.core.cutting;

import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * The gem-cutting rules (M6, decision 5 and Mark's choices, Nov 10):
 * <ul>
 *     <li>A gem's <b>difficulty</b> comes from its quality: Common 1 (topaz), Uncommon 2
 *     (emerald), Rare 3 (ruby, zephyr), Epic 4 (diamond, sapphire, voidstone), Legendary 5.</li>
 *     <li>It needs a <b>minimum level</b> to cut: 1, 2, 4, 6 and 8 by difficulty.</li>
 *     <li>A cut gives <b>XP</b>: 5 per difficulty, half if the gem shatters. Levels run from 1
 *     to {@link #MAX_LEVEL}; level L needs {@code 15 * L * (L - 1) / 2} XP in all.</li>
 *     <li>The <b>rank</b> is rolled. Each level above the gem's minimum (the margin) makes
 *     Flawed less likely and Flawless and Perfect more.</li>
 *     <li>At the minimum level a cut can <b>shatter</b> the gem (8%), one level above
 *     rarely (3%), and after that never.</li>
 * </ul>
 *
 * @author Mark Gottschling on 11/10/2026
 */
public final class GemCutting {
    public static final int MAX_LEVEL = 10;
    private static final int[] MIN_LEVEL = {1, 1, 2, 4, 6, 8};
    private static final Random RANDOM = new Random();

    /**
     * What a cut came to.
     *
     * @param cut the rank, or null if the gem shattered
     * @param xp  the XP it earned
     */
    public record Result(@Nullable GemCut cut, int xp) {
        public boolean shattered() {
            return cut == null;
        }
    }

    private GemCutting() {
    }

    /**
     * @return 1 (Common) to 5 (Legendary)
     */
    public static int difficulty(Gemstone gem) {
        int index = JewelryStats.QUALITIES.indexOf(gem.getQuality());
        return Math.max(1, Math.min(index < 0 ? 1 : index, 5));
    }

    public static int minLevel(Gemstone gem) {
        return MIN_LEVEL[difficulty(gem)];
    }

    /** XP for a cut that doesn't shatter. */
    public static int xp(Gemstone gem) {
        return 5 * difficulty(gem);
    }

    /**
     * @return the total XP that reaches this level
     */
    public static int xpFor(int level) {
        return 15 * level * (level - 1) / 2;
    }

    public static int level(int xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= xpFor(level + 1)) {
            level++;
        }
        return level;
    }

    /**
     * @return the chance in percent of each rank, Flawed to Perfect, then of shattering;
     * they add up to 100
     */
    public static double[] odds(int level, Gemstone gem) {
        int margin = Math.max(0, level - minLevel(gem));
        double shatter = margin == 0 ? 8 : margin == 1 ? 3 : 0;
        double[] weights = {
                Math.max(5, 55 - 10 * margin),
                35,
                8 + 6 * margin,
                1 + 2 * margin,
        };
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double[] odds = new double[5];
        for (int i = 0; i < 4; i++) {
            odds[i] = weights[i] / total * (100 - shatter);
        }
        odds[4] = shatter;
        return odds;
    }

    /**
     * Rolls a cut. The caller has checked the level is at least the gem's minimum.
     */
    public static Result cut(int level, Gemstone gem) {
        double[] odds = odds(level, gem);
        double roll = RANDOM.nextDouble() * 100;
        if (roll < odds[4]) {
            return new Result(null, xp(gem) / 2);
        }
        roll -= odds[4];
        GemCut[] cuts = GemCut.values();
        for (int i = 0; i < cuts.length; i++) {
            if (roll < odds[i] || i == cuts.length - 1) {
                return new Result(cuts[i], xp(gem));
            }
            roll -= odds[i];
        }
        return new Result(GemCut.FINE, xp(gem));
    }
}
