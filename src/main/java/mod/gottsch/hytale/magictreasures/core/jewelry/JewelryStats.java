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
package mod.gottsch.hytale.magictreasures.core.jewelry;

import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryMaterial;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryStyle;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A piece of jewelry's numbers, computed from its definition's material, style and gem
 * with the Magic Treasures 2.0 formulas:
 * <ul>
 *     <li>max mana = (material mana + gem mana) x style mana multiplier, rounded</li>
 *     <li>max uses = material uses x style uses multiplier, rounded</li>
 *     <li>max level = material level + style level</li>
 *     <li>max repairs = material repairs + style repairs</li>
 *     <li>max recharges = material recharges + gem recharges</li>
 *     <li>factors = material factors x gem factors</li>
 *     <li>quality = the higher of the material's and the gem's, plus the style's steps</li>
 * </ul>
 * {@code known} is false when the material or style is missing (a pack was removed). Such a
 * piece keeps its data but can't cast.
 *
 * @param definition the jewelry definition, or null if the item isn't jewelry
 * @param material the material, or null if unknown
 * @param style the style, or null if unknown
 * @param gem the gem, or null if there is none or it is unknown
 * @param known true if the material and style were found
 * @param maxMana the most mana the piece can hold
 * @param maxUses the most uses (durability) it has
 * @param maxLevel the highest spell level it can hold
 * @param maxRepairs how many times it can be repaired
 * @param maxRecharges how many times it can be recharged
 * @param sockets how many runes it can hold
 * @param factors the spell factors
 * @param quality the item quality id, for example {@code Rare}
 * @author Mark Gottschling on 11/09/2026
 */
public record JewelryStats(@Nullable JewelryDefinition definition, @Nullable JewelryMaterial material,
                           @Nullable JewelryStyle style, @Nullable Gemstone gem,
                           boolean known, double maxMana, int maxUses, int maxLevel, int maxRepairs,
                           int maxRecharges, int sockets, SpellFactors factors, String quality) {

    /**
     * Item quality ids in order, lowest first (the vanilla {@code ItemQuality} assets).
     */
    public static final List<String> QUALITIES = List.of("Junk", "Common", "Uncommon", "Rare", "Epic", "Legendary");

    public static final JewelryStats NONE = new JewelryStats(null, null, null, null, false,
            0, 0, 0, 0, 0, 0, SpellFactors.NONE, "Common");

    /**
     * @return the stats of the jewelry item with this id, or {@link #NONE} if it isn't jewelry
     */
    public static JewelryStats of(@Nullable String itemId) {
        return of(JewelryDefinition.get(itemId));
    }

    public static JewelryStats of(@Nullable JewelryDefinition definition) {
        if (definition == null) {
            return NONE;
        }
        JewelryMaterial material = JewelryMaterial.get(definition.getMaterial());
        JewelryStyle style = JewelryStyle.get(definition.getStyle());
        Gemstone gem = Gemstone.get(definition.getGem());
        if (material == null || style == null) {
            return new JewelryStats(definition, material, style, gem, false,
                    0, 0, 0, 0, 0, 0, SpellFactors.NONE, "Common");
        }
        double gemMana = gem == null ? 0 : gem.getMana();
        int gemRecharges = gem == null ? 0 : gem.getRecharges();
        double maxMana = Math.round((material.getMana() + gemMana) * style.getManaMultiplier());
        int maxUses = (int) Math.round(material.getUses() * style.getUsesMultiplier());
        int maxLevel = material.getMaxLevel() + style.getMaxLevel();
        int maxRepairs = material.getRepairs() + style.getRepairs();
        int maxRecharges = material.getRecharges() + gemRecharges;
        SpellFactors factors = gem == null ? material.getFactors() : material.getFactors().multiply(gem.getFactors());
        return new JewelryStats(definition, material, style, gem, true, maxMana, maxUses, maxLevel, maxRepairs,
                maxRecharges, style.getSockets(), factors, quality(material, style, gem));
    }

    /**
     * The higher of the material's and the gem's quality, raised by the style's steps and
     * capped at the highest quality. The jewelry generator script uses the same rule.
     */
    public static String quality(JewelryMaterial material, JewelryStyle style, @Nullable Gemstone gem) {
        int index = Math.max(qualityIndex(material.getQuality()), gem == null ? 0 : qualityIndex(gem.getQuality()));
        index = Math.min(index + style.getQualitySteps(), QUALITIES.size() - 1);
        return QUALITIES.get(index);
    }

    private static int qualityIndex(@Nullable String quality) {
        for (int i = 0; i < QUALITIES.size(); i++) {
            if (QUALITIES.get(i).equalsIgnoreCase(quality)) {
                return i;
            }
        }
        return 1;
    }
}
