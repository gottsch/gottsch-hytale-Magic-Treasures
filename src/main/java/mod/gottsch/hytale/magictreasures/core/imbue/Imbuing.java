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
package mod.gottsch.hytale.magictreasures.core.imbue;

import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import mod.gottsch.hytale.magictreasures.core.element.ElementRolling;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCut;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;

import com.hypixel.hytale.server.core.Message;
import mod.gottsch.hytale.magictreasures.core.lang.Lang;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * The rules for working on a piece of jewelry with another item, ported from the Minecraft
 * anvil (Magic Treasures 2.0, {@code AnvilEventHandler}):
 * <ul>
 *     <li><b>Spell scroll</b> ({@code MT_Scroll_<SpellId>}): imbues the spell, if the piece
 *     has none yet and its spell level is high enough. One spell per piece, as in
 *     Minecraft.</li>
 *     <li><b>Core rune</b> (a vanilla {@code Rune_*} with an ability): socketed, if a socket
 *     is free and the piece doesn't hold that rune already.</li>
 *     <li><b>Cut gem</b> ({@code MT_Gem_<Gem>_<Cut>}, M6): set, if the piece has no gem and
 *     the gemmed item exists. The piece becomes that item and keeps its state, plus the
 *     gem's extra charge and recharges, scaled by the cut, which the state records. A raw
 *     gem ({@code Rock_Gem_*}) is listed but refused: it must be cut first.</li>
 *     <li><b>Crystal</b> (any {@code Ingredient_Crystal_*} but Void, Mark's choice Nov 10):
 *     refills the charge and uses one recharge.</li>
 *     <li><b>Another piece of jewelry</b>: repairs, refilling the uses and using one
 *     repair. The other piece is used up.</li>
 * </ul>
 * A named piece (Nov 13) refuses a recharge if it is {@code Unlimited} and a repair if it is
 * {@code Unbreakable}, and is never used up to repair another piece.
 * Each item is used up, one at a time.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public final class Imbuing {
    public static final String SCROLL_PREFIX = "MT_Scroll_";
    public static final String CRYSTAL_PREFIX = "Ingredient_Crystal_";
    public static final String VOID_CRYSTAL = "Ingredient_Crystal_Void";

    /** What an item does to a piece of jewelry. */
    public enum Kind { SPELL, RUNE, GEM, RECHARGE, REPAIR }

    /**
     * The result of checking an item against a piece.
     *
     * @param kind    what the item does
     * @param result  the piece afterwards, or null if the item can't be used on it
     * @param text    for the page: what will happen, or why it can't
     * @param message for chat, once done (translation keys, magictreasures.imbue.*, v1.2)
     */
    public record Outcome(Kind kind, @Nullable ItemStack result, Message text, Message message) {
        public boolean allowed() {
            return result != null;
        }
    }

    private Imbuing() {
    }

    /**
     * @return what the material does to the piece, or null if the material is nothing
     * jewelry uses (or the piece isn't jewelry)
     */
    @Nullable
    public static Outcome check(@Nullable ItemStack piece, @Nullable ItemStack material) {
        if (ItemStack.isEmpty(piece) || ItemStack.isEmpty(material) || !Jewelry.isJewelry(piece)) {
            return null;
        }
        Kind kind = kindOf(material);
        if (kind == null) {
            return null;
        }
        JewelryStats stats = Jewelry.stats(piece);
        if (!stats.known()) {
            return refused(kind, "unknownPiece");
        }
        JewelryState state = Jewelry.state(piece);
        Message name = Jewelry.name(piece);
        String id = material.getItemId();
        return switch (kind) {
            case SPELL -> spell(piece, stats, state, name, Spell.get(id.substring(SCROLL_PREFIX.length())));
            case RUNE -> rune(piece, stats, state, name, id);
            case GEM -> gem(piece, stats, state, name, id);
            case RECHARGE -> recharge(piece, stats, state, name);
            // a named piece is never used up to repair another (Nov 13)
            case REPAIR -> Jewelry.stats(material).definition() != null && Jewelry.stats(material).definition().isNamed()
                    ? refused(Kind.REPAIR, Lang.of("imbue.oneOfAKind").param("piece", Jewelry.name(material)))
                    : repair(piece, stats, state, name);
        };
    }

    /**
     * @return what kind of jewelry material this item is, or null if it is none
     */
    @Nullable
    public static Kind kindOf(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        String id = stack.getItemId();
        if (id.startsWith(SCROLL_PREFIX)) {
            return Spell.get(id.substring(SCROLL_PREFIX.length())) == null ? null : Kind.SPELL;
        }
        if (id.startsWith(CRYSTAL_PREFIX) && !VOID_CRYSTAL.equals(id)) {
            return Kind.RECHARGE;
        }
        if (Jewelry.isJewelry(stack)) {
            return Kind.REPAIR;
        }
        if (GemCut.ofItem(id) != null || GemCut.gemOfRaw(id) != null) {
            // cut gems are set; raw ones are listed, refused, with the reason
            return Kind.GEM;
        }
        Item item = stack.getItem();
        if (item != null && item.getAbility() instanceof CoreItemAbility) {
            return Kind.RUNE;
        }
        return null;
    }

    private static Outcome spell(ItemStack piece, JewelryStats stats, JewelryState state, Message name,
                                 @Nullable Spell spell) {
        if (spell == null) {
            return refused(Kind.SPELL, "unknownSpell");
        }
        if (state.getSpells().length > 0) {
            return refused(Kind.SPELL, Lang.of("imbue.holdsSpell").param("spell", Lang.spell(state.getSpells()[0])));
        }
        if (spell.getLevel() > stats.maxLevel()) {
            return refused(Kind.SPELL, Lang.of("imbue.spellLevel").param("spell", Lang.spell(spell.getId()))
                    .param("level", spell.getLevel()).param("max", stats.maxLevel()));
        }
        JewelryState next = state.copy().setSpells(spell.getId());
        return new Outcome(Kind.SPELL, Jewelry.withState(piece, next),
                Lang.of("imbue.spell").param("spell", Lang.spell(spell.getId())),
                Lang.of("imbue.spellDone").param("piece", name).param("spell", Lang.spell(spell.getId())));
    }

    private static Outcome rune(ItemStack piece, JewelryStats stats, JewelryState state, Message name,
                                String runeId) {
        Message runeName = Lang.item(runeId);
        if (Arrays.asList(state.getRunes()).contains(runeId)) {
            return refused(Kind.RUNE, Lang.of("imbue.holdsRune").param("rune", runeName));
        }
        if (state.getRunes().length >= stats.sockets()) {
            return refused(Kind.RUNE, Lang.of("imbue.socketsFull").param("sockets", stats.sockets()));
        }
        String[] runes = Arrays.copyOf(state.getRunes(), state.getRunes().length + 1);
        runes[runes.length - 1] = runeId;
        JewelryState next = state.copy().setRunes(runes);
        return new Outcome(Kind.RUNE, Jewelry.withState(piece, next),
                Lang.of("imbue.rune").param("rune", runeName),
                Lang.of("imbue.runeDone").param("rune", runeName).param("piece", name));
    }

    private static Outcome gem(ItemStack piece, JewelryStats stats, JewelryState state, Message name,
                               String gemItemId) {
        if (GemCut.gemOfRaw(gemItemId) != null) {
            return refused(Kind.GEM, "rawGem");
        }
        GemCut cut = GemCut.ofItem(gemItemId);
        Gemstone gem = GemCut.gemOfItem(gemItemId);
        if (cut == null || gem == null) {
            return refused(Kind.GEM, "unknownGem");
        }
        if (stats.gem() != null) {
            return refused(Kind.GEM, Lang.of("imbue.hasGem").param("gem", Lang.source(stats.gem().getId())));
        }
        JewelryDefinition definition = stats.definition();
        String targetId = gemmedItemId(definition, gem.getId());
        if (targetId == null) {
            return refused(Kind.GEM, "noSuchPiece");
        }
        JewelryStats target = JewelryStats.of(JewelryDefinition.get(targetId), cut);
        // as in Minecraft: the gem adds its extra charge and recharges to the current values
        double extraMana = Math.max(0, target.maxMana() - stats.maxMana());
        int extraRecharges = Math.max(0, target.maxRecharges() - stats.maxRecharges());
        JewelryState next = state.copy()
                .setMana(Math.min(target.maxMana(), state.getMana() + extraMana))
                .setRecharges(Math.min(target.maxRecharges(), state.getRecharges() + extraRecharges))
                .setCut(cut.getName());
        if (next.isRolled()) {
            // the metal was rolled already: add the gem's roll for its cut (an unrolled piece
            // rolls metal and gem together when written)
            next = ElementRolling.addGem(next, gem, cut);
        }
        ItemStack result = Jewelry.withState(new ItemStack(targetId, 1), next);
        return new Outcome(Kind.GEM, result,
                Lang.of("imbue.gem").param("charge", String.format("%.0f", extraMana))
                        .param("recharges", extraRecharges),
                Lang.of("imbue.gemDone").param("piece", name).param("result", Jewelry.name(result))
                        .param("gem", Lang.item(gemItemId)));
    }

    private static Outcome recharge(ItemStack piece, JewelryStats stats, JewelryState state, Message name) {
        if (stats.definition().isUnlimited()) {
            return refused(Kind.RECHARGE, "neverRunsOut");
        }
        if (state.getRecharges() <= 0) {
            return refused(Kind.RECHARGE, "noRecharges");
        }
        if (state.getMana() >= stats.maxMana()) {
            return refused(Kind.RECHARGE, "chargeFull");
        }
        JewelryState next = state.copy().setMana(stats.maxMana()).setRecharges(state.getRecharges() - 1);
        String from = String.format("%.0f", state.getMana());
        String to = String.format("%.0f", stats.maxMana());
        return new Outcome(Kind.RECHARGE, Jewelry.withState(piece, next),
                Lang.of("imbue.recharge").param("from", from).param("to", to).param("left", next.getRecharges()),
                Lang.of("imbue.rechargeDone").param("piece", name).param("to", to)
                        .param("left", next.getRecharges()));
    }

    private static Outcome repair(ItemStack piece, JewelryStats stats, JewelryState state, Message name) {
        if (stats.definition().isUnbreakable()) {
            return refused(Kind.REPAIR, "cantWearOut");
        }
        if (state.getRepairs() <= 0) {
            return refused(Kind.REPAIR, "noRepairs");
        }
        if (state.getUses() >= stats.maxUses()) {
            return refused(Kind.REPAIR, "usesFull");
        }
        JewelryState next = state.copy().setUses(stats.maxUses()).setRepairs(state.getRepairs() - 1);
        return new Outcome(Kind.REPAIR, Jewelry.withState(piece, next),
                Lang.of("imbue.repair").param("from", state.getUses()).param("to", stats.maxUses())
                        .param("left", next.getRepairs()),
                Lang.of("imbue.repairDone").param("piece", name).param("to", stats.maxUses())
                        .param("left", next.getRepairs()));
    }

    /** @return a refusal whose reason is {@code magictreasures.imbue.<key>} */
    private static Outcome refused(Kind kind, String key) {
        return refused(kind, Lang.of("imbue." + key));
    }

    private static Outcome refused(Kind kind, Message why) {
        return new Outcome(kind, null, why, why);
    }

    /**
     * @return the id of the jewelry item like this one but set with the gem, or null if
     * there is no such item
     */
    @Nullable
    public static String gemmedItemId(JewelryDefinition definition, String gemId) {
        for (JewelryDefinition other : JewelryDefinition.getAssetMap().getAssetMap().values()) {
            if (Objects.equals(other.getType(), definition.getType())
                    && Objects.equals(other.getMaterial(), definition.getMaterial())
                    && Objects.equals(other.getStyle(), definition.getStyle())
                    && gemId.equalsIgnoreCase(other.getGem())
                    && Item.getAssetMap().getAsset(other.getId()) != null) {
                return other.getId();
            }
        }
        return null;
    }
}
