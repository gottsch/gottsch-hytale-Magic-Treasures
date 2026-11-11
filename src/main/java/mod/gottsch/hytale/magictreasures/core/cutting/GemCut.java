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
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;

import javax.annotation.Nullable;

/**
 * How well a gem is cut (M6, decision Nov 10: four ranks, each its own item). A raw gem
 * ({@code Rock_Gem_<Gem>}) is cut with a gem cutter's kit into
 * {@code MT_Gem_<Gem>_<Cut>}; only cut gems can be set in jewelry.
 * <p>
 * The cut scales what the gem adds to a piece: its extra charge, its recharges and how far
 * its rune factors move from 1. A Fine cut gives the gem's listed stats. The cut also raises
 * or lowers the cut gem's item quality from the gem's own.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public enum GemCut {
    FLAWED("Flawed", 0.75, -1),
    FINE("Fine", 1.0, 0),
    FLAWLESS("Flawless", 1.25, 1),
    PERFECT("Perfect", 1.5, 2);

    /** Cut gem item ids: {@code MT_Gem_<Gem>_<Cut>}. */
    public static final String PREFIX = "MT_Gem_";
    /** Raw (vanilla) gem item ids. */
    public static final String RAW_PREFIX = "Rock_Gem_";

    private final String name;
    private final double multiplier;
    private final int qualitySteps;

    GemCut(String name, double multiplier, int qualitySteps) {
        this.name = name;
        this.multiplier = multiplier;
        this.qualitySteps = qualitySteps;
    }

    public String getName() {
        return name;
    }

    /** What the gem's extra charge and recharges are multiplied by. */
    public double getMultiplier() {
        return multiplier;
    }

    /** Quality tiers above (or below) the gem's own. */
    public int getQualitySteps() {
        return qualitySteps;
    }

    /**
     * @return the cut gem item id, e.g. {@code MT_Gem_Ruby_Flawless}
     */
    public String itemId(Gemstone gem) {
        return PREFIX + gem.getId() + "_" + name;
    }

    /**
     * @return the cut gem item's quality
     */
    public String quality(Gemstone gem) {
        int index = JewelryStats.QUALITIES.indexOf(gem.getQuality());
        index = Math.max(0, Math.min((index < 0 ? 1 : index) + qualitySteps, JewelryStats.QUALITIES.size() - 1));
        return JewelryStats.QUALITIES.get(index);
    }

    /**
     * @return the gem's rune factors with each one's distance from 1 scaled by this cut
     */
    public SpellFactors scale(SpellFactors factors) {
        return new SpellFactors(scale(factors.getCost()), scale(factors.getCooldown()), scale(factors.getEffect()),
                scale(factors.getDuration()), scale(factors.getRange()));
    }

    private double scale(double factor) {
        return 1 + (factor - 1) * multiplier;
    }

    /**
     * @return the cut with this name (any case), or null
     */
    @Nullable
    public static GemCut byName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (GemCut cut : values()) {
            if (cut.name.equalsIgnoreCase(name)) {
                return cut;
            }
        }
        return null;
    }

    /**
     * @return the cut of a cut gem item id, or null if it isn't one
     */
    @Nullable
    public static GemCut ofItem(@Nullable String itemId) {
        if (itemId == null || !itemId.startsWith(PREFIX)) {
            return null;
        }
        int at = itemId.lastIndexOf('_');
        return at < PREFIX.length() ? null : byName(itemId.substring(at + 1));
    }

    /**
     * @return the gemstone of a cut gem item id, or null if it isn't one
     */
    @Nullable
    public static Gemstone gemOfItem(@Nullable String itemId) {
        if (ofItem(itemId) == null) {
            return null;
        }
        return Gemstone.get(itemId.substring(PREFIX.length(), itemId.lastIndexOf('_')));
    }

    /**
     * @return the gemstone of a raw gem item id ({@code Rock_Gem_Ruby}), or null
     */
    @Nullable
    public static Gemstone gemOfRaw(@Nullable String itemId) {
        if (itemId == null) {
            return null;
        }
        for (Gemstone gem : Gemstone.getAssetMap().getAssetMap().values()) {
            if (itemId.equals(gem.getItem())) {
                return gem;
            }
        }
        return null;
    }
}
