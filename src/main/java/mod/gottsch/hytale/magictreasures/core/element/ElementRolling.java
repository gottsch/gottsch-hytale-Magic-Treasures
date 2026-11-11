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

import mod.gottsch.hytale.magictreasures.core.cutting.GemCut;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryMaterial;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryStyle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls a piece's elemental properties (decided with Mark, Nov 11):
 * <ul>
 *     <li><b>Metal</b>, once, when the piece is made or first seen: each of the material's
 *     {@code Elements} comes up with its {@code Chance}, at a value rolled in its range. The
 *     style's {@code ElementRerolls} give a missed property more tries, and
 *     {@code ElementGuaranteed} (Lords) makes sure at least one good property comes up.</li>
 *     <li><b>Gem</b>, when it is set, by its cut:
 *     <ul>
 *         <li>Flawed: no bonus; a 50% chance of a weakness (-5% to -10%) to the gem's
 *         {@code Weakness} element;</li>
 *         <li>Fine: its resistances and damage at half strength, no on-hit effect;</li>
 *         <li>Flawless: everything, rolled in range;</li>
 *         <li>Perfect: everything at the top of its range, on-hit chances x1.5, and a 25%
 *         chance of its {@code Bonus} too.</li>
 *     </ul></li>
 * </ul>
 * A gem's roll lives on the piece, not on the cut gem, so cut gems still stack.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public final class ElementRolling {
    static final double FLAWED_WEAKNESS_CHANCE = 0.5;
    static final double FLAWED_WEAKNESS_MIN = -0.10;
    static final double FLAWED_WEAKNESS_MAX = -0.05;
    static final double FINE_SHARE = 0.5;
    static final double PERFECT_ON_HIT = 1.5;
    static final double PERFECT_BONUS_CHANCE = 0.25;
    /** The element a property not tied to one (a crit) is saved with. */
    public static final String ANY = "Any";

    private ElementRolling() {
    }

    /**
     * @return the state with its metal (and, if it has one, its gem) rolled, or the same state
     * if it was rolled already
     */
    public static JewelryState ensureRolled(@Nullable JewelryDefinition definition, JewelryState state) {
        if (state.isRolled() || definition == null) {
            return state;
        }
        Random random = ThreadLocalRandom.current();
        List<ElementProperty> rolled = new ArrayList<>(rollMetal(definition, random));
        Gemstone gem = Gemstone.get(definition.getGem());
        if (gem != null) {
            GemCut cut = GemCut.byName(state.getCut());
            rolled.addAll(rollGem(gem, cut == null ? GemCut.FINE : cut, random));
        }
        return state.copy().setElements(encode(rolled)).setRolled(true);
    }

    /**
     * @return the state with the gem's properties added to what it already has
     */
    public static JewelryState addGem(JewelryState state, Gemstone gem, GemCut cut) {
        List<String> all = new ArrayList<>(Arrays.asList(state.getElements()));
        all.addAll(Arrays.asList(encode(rollGem(gem, cut, ThreadLocalRandom.current()))));
        return state.copy().setElements(all.toArray(new String[0]));
    }

    static List<ElementProperty> rollMetal(JewelryDefinition definition, Random random) {
        JewelryMaterial material = JewelryMaterial.get(definition.getMaterial());
        if (material == null) {
            return List.of();
        }
        JewelryStyle style = JewelryStyle.get(definition.getStyle());
        int tries = 1 + (style == null ? 0 : Math.max(0, style.getElementRerolls()));
        List<ElementProperty> out = new ArrayList<>();
        for (ElementRoll roll : material.getElements()) {
            for (int i = 0; i < tries; i++) {
                if (random.nextDouble() < roll.getChance()) {
                    ElementProperty property = property(roll, inRange(roll, random), random);
                    if (property != null) {
                        out.add(property);
                    }
                    break;
                }
            }
        }
        if (style != null && style.isElementGuaranteed() && out.stream().noneMatch(ElementProperty::good)) {
            // the first good property the metal has, rolled in range
            for (ElementRoll roll : material.getElements()) {
                if (roll.getMax() > 0) {
                    ElementProperty property = property(roll, inRange(roll, random), random);
                    if (property != null) {
                        out.add(property);
                    }
                    break;
                }
            }
        }
        return out;
    }

    static List<ElementProperty> rollGem(Gemstone gem, GemCut cut, Random random) {
        List<ElementProperty> out = new ArrayList<>();
        switch (cut) {
            case FLAWED -> {
                if (gem.getWeakness() != null && random.nextDouble() < FLAWED_WEAKNESS_CHANCE) {
                    double value = FLAWED_WEAKNESS_MIN + random.nextDouble() * (FLAWED_WEAKNESS_MAX - FLAWED_WEAKNESS_MIN);
                    out.add(new ElementProperty(ElementProperty.Kind.RESISTANCE, gem.getWeakness(), value, null));
                }
            }
            case FINE -> {
                for (ElementRoll roll : gem.getElements()) {
                    // on-hit chances (statuses, crits) need a Flawless cut
                    if (roll.getKind() != ElementProperty.Kind.ON_HIT && roll.getKind() != ElementProperty.Kind.CRIT) {
                        add(out, property(roll, inRange(roll, random) * FINE_SHARE, random));
                    }
                }
            }
            case FLAWLESS -> {
                for (ElementRoll roll : gem.getElements()) {
                    add(out, property(roll, inRange(roll, random), random));
                }
            }
            case PERFECT -> {
                for (ElementRoll roll : gem.getElements()) {
                    double value = top(roll);
                    if (roll.getKind() == ElementProperty.Kind.ON_HIT || roll.getKind() == ElementProperty.Kind.CRIT) {
                        value *= PERFECT_ON_HIT;
                    }
                    add(out, property(roll, value, random));
                }
                if (random.nextDouble() < PERFECT_BONUS_CHANCE) {
                    for (ElementRoll roll : gem.getBonus()) {
                        add(out, property(roll, top(roll), random));
                    }
                }
            }
        }
        return out;
    }

    private static void add(List<ElementProperty> out, @Nullable ElementProperty property) {
        if (property != null) {
            out.add(property);
        }
    }

    /**
     * @return the roll's best value: the top of its range, or for a weakness the mildest
     */
    private static double top(ElementRoll roll) {
        return roll.getMax();
    }

    private static double inRange(ElementRoll roll, Random random) {
        return roll.getMin() + random.nextDouble() * (roll.getMax() - roll.getMin());
    }

    @Nullable
    private static ElementProperty property(ElementRoll roll, double value, Random random) {
        ElementProperty.Kind kind = roll.getKind();
        String element = roll.getElement();
        if (kind == ElementProperty.Kind.CRIT && element == null) {
            element = ANY;
        }
        if (kind == null || element == null) {
            return null;
        }
        // two decimals, so tooltips and totals read cleanly
        value = Math.round(value * 100) / 100.0;
        if (value == 0) {
            return null;
        }
        return new ElementProperty(kind, element, value, roll.getEffect());
    }

    private static String[] encode(List<ElementProperty> properties) {
        return properties.stream().map(ElementProperty::encode).toArray(String[]::new);
    }

    /**
     * @return the properties saved on a state (damaged entries skipped)
     */
    public static List<ElementProperty> decode(JewelryState state) {
        List<ElementProperty> out = new ArrayList<>();
        for (String text : state.getElements()) {
            add(out, ElementProperty.decode(text));
        }
        return out;
    }
}
