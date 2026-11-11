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

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.Arrays;

/**
 * What changes on a piece of jewelry in play: its mana, uses, repairs and recharges left,
 * and its spells and runes. It is saved in the item stack's metadata under the key
 * {@code MagicTreasures}. Everything else about a piece (its maximums, level and factors)
 * is computed from its assets when needed, see {@link JewelryStats}, so changing a
 * material's JSON changes every piece already in the world.
 * <p>
 * Treat instances as values: change a {@link #copy()} and write it to a new stack with
 * {@link Jewelry#withState}.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class JewelryState {
    public static final String KEY = "MagicTreasures";

    public static final BuilderCodec<JewelryState> CODEC = BuilderCodec.builder(JewelryState.class, JewelryState::new)
            .append(new KeyedCodec<>("Mana", Codec.DOUBLE), (s, v) -> s.mana = v, s -> s.mana).add()
            .append(new KeyedCodec<>("Uses", Codec.INTEGER), (s, v) -> s.uses = v, s -> s.uses).add()
            .append(new KeyedCodec<>("Repairs", Codec.INTEGER), (s, v) -> s.repairs = v, s -> s.repairs).add()
            .append(new KeyedCodec<>("Recharges", Codec.INTEGER), (s, v) -> s.recharges = v, s -> s.recharges).add()
            .append(new KeyedCodec<>("Spells", Codec.STRING_ARRAY), (s, v) -> s.spells = v, s -> s.spells).add()
            .append(new KeyedCodec<>("Runes", Codec.STRING_ARRAY), (s, v) -> s.runes = v, s -> s.runes).add()
            // how well its gem is cut (GemCut name); none on pieces set before M6, read as Fine
            .append(new KeyedCodec<>("Cut", Codec.STRING), (s, v) -> s.cut = v, s -> s.cut).add()
            // elemental properties rolled for the piece (ElementProperty.encode); Rolled marks a
            // piece whose metal (and gem) were rolled, even if nothing came up
            .append(new KeyedCodec<>("Elements", Codec.STRING_ARRAY), (s, v) -> s.elements = v, s -> s.elements).add()
            .append(new KeyedCodec<>("Rolled", Codec.BOOLEAN), (s, v) -> s.rolled = v, s -> s.rolled).add()
            .build();

    public static final KeyedCodec<JewelryState> KEYED_CODEC = new KeyedCodec<>(KEY, CODEC);

    private static final String[] NONE = new String[0];

    protected double mana;
    protected int uses;
    protected int repairs;
    protected int recharges;
    protected String[] spells = NONE;
    protected String[] runes = NONE;
    protected String cut;
    protected String[] elements = NONE;
    protected Boolean rolled;

    public JewelryState() {
    }

    /**
     * @return a new piece's state: everything full, no spells or runes
     */
    public static JewelryState fresh(JewelryStats stats) {
        JewelryState state = new JewelryState();
        state.mana = stats.maxMana();
        state.uses = stats.maxUses();
        state.repairs = stats.maxRepairs();
        state.recharges = stats.maxRecharges();
        return state;
    }

    public JewelryState copy() {
        JewelryState c = new JewelryState();
        c.mana = mana;
        c.uses = uses;
        c.repairs = repairs;
        c.recharges = recharges;
        c.spells = getSpells().clone();
        c.runes = getRunes().clone();
        c.cut = cut;
        c.elements = getElements().clone();
        c.rolled = rolled;
        return c;
    }

    /**
     * @return the piece's rolled elemental properties, encoded ({@code ElementProperty})
     */
    public String[] getElements() {
        return elements == null ? NONE : elements;
    }

    public JewelryState setElements(String... elements) {
        this.elements = elements == null ? NONE : elements;
        return this;
    }

    /**
     * @return true once the piece's elemental properties were rolled
     */
    public boolean isRolled() {
        return rolled != null && rolled;
    }

    public JewelryState setRolled(boolean rolled) {
        this.rolled = rolled;
        return this;
    }

    public double getMana() {
        return mana;
    }

    public JewelryState setMana(double mana) {
        this.mana = mana;
        return this;
    }

    public int getUses() {
        return uses;
    }

    public JewelryState setUses(int uses) {
        this.uses = uses;
        return this;
    }

    public int getRepairs() {
        return repairs;
    }

    public JewelryState setRepairs(int repairs) {
        this.repairs = repairs;
        return this;
    }

    public int getRecharges() {
        return recharges;
    }

    public JewelryState setRecharges(int recharges) {
        this.recharges = recharges;
        return this;
    }

    public String[] getSpells() {
        return spells == null ? NONE : spells;
    }

    public JewelryState setSpells(String... spells) {
        this.spells = spells == null ? NONE : spells;
        return this;
    }

    public String[] getRunes() {
        return runes == null ? NONE : runes;
    }

    public JewelryState setRunes(String... runes) {
        this.runes = runes == null ? NONE : runes;
        return this;
    }

    /**
     * @return the name of the gem's cut ({@code GemCut}), or null if not recorded
     */
    public String getCut() {
        return cut;
    }

    public JewelryState setCut(String cut) {
        this.cut = cut;
        return this;
    }

    @Override
    public String toString() {
        return String.format("mana %.1f, uses %d, repairs %d, recharges %d, spells %s, runes %s",
                mana, uses, repairs, recharges, Arrays.toString(getSpells()), Arrays.toString(getRunes()));
    }
}
