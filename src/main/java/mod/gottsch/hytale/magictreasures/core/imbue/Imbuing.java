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
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;

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
 *     <li><b>Gem</b> (an item named by a gemstone asset): set, if the piece has no gem and
 *     the gemmed item exists. The piece becomes that item and keeps its state, plus the
 *     gem's extra charge and recharges.</li>
 *     <li><b>Crystal</b> (any {@code Ingredient_Crystal_*} but Void, Mark's choice Nov 10):
 *     refills the charge and uses one recharge.</li>
 *     <li><b>Another piece of jewelry</b>: repairs, refilling the uses and using one
 *     repair. The other piece is used up.</li>
 * </ul>
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
     * @param message for chat, once done
     */
    public record Outcome(Kind kind, @Nullable ItemStack result, String text, String message) {
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
            return refused(kind, "This piece's material or style is missing");
        }
        JewelryState state = Jewelry.state(piece);
        String name = Jewelry.displayName(piece);
        String id = material.getItemId();
        return switch (kind) {
            case SPELL -> spell(piece, stats, state, name, Spell.get(id.substring(SCROLL_PREFIX.length())));
            case RUNE -> rune(piece, stats, state, name, id);
            case GEM -> gem(piece, stats, state, name, gemOf(id));
            case RECHARGE -> recharge(piece, stats, state, name);
            case REPAIR -> repair(piece, stats, state, name);
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
        if (gemOf(id) != null) {
            return Kind.GEM;
        }
        Item item = stack.getItem();
        if (item != null && item.getAbility() instanceof CoreItemAbility) {
            return Kind.RUNE;
        }
        return null;
    }

    private static Outcome spell(ItemStack piece, JewelryStats stats, JewelryState state, String name,
                                 @Nullable Spell spell) {
        if (spell == null) {
            return refused(Kind.SPELL, "Unknown spell");
        }
        if (state.getSpells().length > 0) {
            Spell held = Spell.get(state.getSpells()[0]);
            return refused(Kind.SPELL, "Already holds " + (held == null ? state.getSpells()[0] : held.getName()));
        }
        if (spell.getLevel() > stats.maxLevel()) {
            return refused(Kind.SPELL, spell.getName() + " is level " + spell.getLevel()
                    + "; this piece takes up to " + stats.maxLevel());
        }
        JewelryState next = state.copy().setSpells(spell.getId());
        return new Outcome(Kind.SPELL, Jewelry.withState(piece, next), "Imbue with " + spell.getName(),
                "Your " + name + " is imbued with " + spell.getName() + ".");
    }

    private static Outcome rune(ItemStack piece, JewelryStats stats, JewelryState state, String name, String runeId) {
        String runeName = runeId.replace("Rune_", "");
        if (Arrays.asList(state.getRunes()).contains(runeId)) {
            return refused(Kind.RUNE, "Already holds " + runeName);
        }
        if (state.getRunes().length >= stats.sockets()) {
            return refused(Kind.RUNE, "Rune sockets full (" + stats.sockets() + ")");
        }
        String[] runes = Arrays.copyOf(state.getRunes(), state.getRunes().length + 1);
        runes[runes.length - 1] = runeId;
        JewelryState next = state.copy().setRunes(runes);
        return new Outcome(Kind.RUNE, Jewelry.withState(piece, next), "Socket " + runeName,
                runeName + " is socketed in your " + name + ". Casting " + runeName
                        + " from your ability slots is now boosted while you wear it.");
    }

    private static Outcome gem(ItemStack piece, JewelryStats stats, JewelryState state, String name,
                               @Nullable Gemstone gem) {
        if (gem == null) {
            return refused(Kind.GEM, "Unknown gem");
        }
        if (stats.gem() != null) {
            return refused(Kind.GEM, "Already set with a " + stats.gem().getId().toLowerCase());
        }
        JewelryDefinition definition = stats.definition();
        String targetId = gemmedItemId(definition, gem.getId());
        if (targetId == null) {
            return refused(Kind.GEM, "No " + definition.getMaterial() + " " + gem.getId() + " "
                    + definition.getType() + " yet");
        }
        JewelryStats target = JewelryStats.of(targetId);
        // as in Minecraft: the gem adds its extra charge and recharges to the current values
        double extraMana = Math.max(0, target.maxMana() - stats.maxMana());
        int extraRecharges = Math.max(0, target.maxRecharges() - stats.maxRecharges());
        JewelryState next = state.copy()
                .setMana(Math.min(target.maxMana(), state.getMana() + extraMana))
                .setRecharges(Math.min(target.maxRecharges(), state.getRecharges() + extraRecharges));
        ItemStack result = Jewelry.withState(new ItemStack(targetId, 1), next);
        return new Outcome(Kind.GEM, result, String.format("Set: charge +%.0f, recharges +%d", extraMana, extraRecharges),
                "Your " + name + " is now a " + Jewelry.displayName(result) + ".");
    }

    private static Outcome recharge(ItemStack piece, JewelryStats stats, JewelryState state, String name) {
        if (state.getRecharges() <= 0) {
            return refused(Kind.RECHARGE, "No recharges left");
        }
        if (state.getMana() >= stats.maxMana()) {
            return refused(Kind.RECHARGE, "Charge is full");
        }
        JewelryState next = state.copy().setMana(stats.maxMana()).setRecharges(state.getRecharges() - 1);
        return new Outcome(Kind.RECHARGE, Jewelry.withState(piece, next),
                String.format("Recharge: %.0f -> %.0f (%d left)", state.getMana(), stats.maxMana(), next.getRecharges()),
                String.format("Your %s is recharged to %.0f. Recharges left: %d.", name, stats.maxMana(),
                        next.getRecharges()));
    }

    private static Outcome repair(ItemStack piece, JewelryStats stats, JewelryState state, String name) {
        if (state.getRepairs() <= 0) {
            return refused(Kind.REPAIR, "No repairs left");
        }
        if (state.getUses() >= stats.maxUses()) {
            return refused(Kind.REPAIR, "Uses are full");
        }
        JewelryState next = state.copy().setUses(stats.maxUses()).setRepairs(state.getRepairs() - 1);
        return new Outcome(Kind.REPAIR, Jewelry.withState(piece, next),
                String.format("Repair: uses %d -> %d (%d left)", state.getUses(), stats.maxUses(), next.getRepairs()),
                String.format("Your %s is repaired to %d uses. Repairs left: %d.", name, stats.maxUses(),
                        next.getRepairs()));
    }

    private static Outcome refused(Kind kind, String why) {
        return new Outcome(kind, null, why, why + ".");
    }

    /**
     * @return the gemstone whose item this is, or null
     */
    @Nullable
    public static Gemstone gemOf(String itemId) {
        for (Gemstone gem : Gemstone.getAssetMap().getAssetMap().values()) {
            if (itemId.equals(gem.getItem())) {
                return gem;
            }
        }
        return null;
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
