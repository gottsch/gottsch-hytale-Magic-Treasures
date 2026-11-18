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

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCut;
import mod.gottsch.hytale.magictreasures.core.element.ElementProperty;
import mod.gottsch.hytale.magictreasures.core.element.ElementRolling;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryStyle;
import mod.gottsch.hytale.magictreasures.core.lang.Lang;
import mod.gottsch.hytale.magictreasures.core.spell.SpellTooltip;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for jewelry item stacks: whether a stack is jewelry, reading and writing its
 * {@link JewelryState}, and making new pieces.
 * <p>
 * Writing a state also rewrites the stack's tooltip: the per-stack {@code ItemDisplay}
 * description, which the client shows in place of the item asset's description.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class Jewelry {
    private static final String LABEL = "#a0a0a0";
    private static final String VALUE = "#ffffff";
    private static final String MANA = "#5aa9ff";
    private static final String GOOD = "#7ddc7d";
    private static final String BAD = "#ff7070";
    /** A named piece's lore line: a muted gold. */
    private static final String LORE = "#d8b46a";

    private Jewelry() {
    }

    public static boolean isJewelry(@Nullable ItemStack stack) {
        return definition(stack) != null;
    }

    @Nullable
    public static JewelryDefinition definition(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return JewelryDefinition.get(stack.getItemId());
    }

    /**
     * @return the piece's stats, with its gem's cut (from its state) applied
     */
    public static JewelryStats stats(@Nullable ItemStack stack) {
        JewelryDefinition definition = definition(stack);
        if (definition == null) {
            return JewelryStats.NONE;
        }
        JewelryState state = stack.getFromMetadataOrNull(JewelryState.KEYED_CODEC);
        return JewelryStats.of(definition, GemCut.byName(state == null || state.getCut() == null
                ? definition.getCut() : state.getCut()));
    }

    /**
     * @return the stack's state, or a fresh one if it has none yet (for example a piece
     * from a creative menu or another mod's loot); null if the stack isn't jewelry
     */
    @Nullable
    public static JewelryState state(@Nullable ItemStack stack) {
        JewelryDefinition definition = definition(stack);
        if (definition == null) {
            return null;
        }
        JewelryState state = stack.getFromMetadataOrNull(JewelryState.KEYED_CODEC);
        if (state == null) {
            return JewelryState.fresh(JewelryStats.of(definition, GemCut.byName(definition.getCut())));
        }
        if (state.getCut() == null && definition.getGem() != null && definition.getCut() != null) {
            // a named piece saved before its cut was recorded (Nov 13)
            state = state.copy().setCut(definition.getCut());
        }
        // a piece made before its stats were lowered (Nov 10) keeps no more than its maximums
        JewelryStats stats = JewelryStats.of(definition, GemCut.byName(state.getCut()));
        if (stats.known() && (state.getMana() > stats.maxMana() || state.getUses() > stats.maxUses()
                || state.getRecharges() > stats.maxRecharges() || state.getRepairs() > stats.maxRepairs())) {
            state = state.copy()
                    .setMana(Math.min(state.getMana(), stats.maxMana()))
                    .setUses(Math.min(state.getUses(), stats.maxUses()))
                    .setRecharges(Math.min(state.getRecharges(), stats.maxRecharges()))
                    .setRepairs(Math.min(state.getRepairs(), stats.maxRepairs()));
        }
        return state;
    }

    /**
     * @return a new stack of the jewelry item with this id, with a fresh state and tooltip,
     * or null if the id isn't a jewelry item
     */
    @Nullable
    public static ItemStack create(String itemId) {
        return create(itemId, null);
    }

    /**
     * @param cut the gem's cut for a gemmed piece ({@code GemCut} name), or null for Fine
     * @return a new stack of the jewelry item with this id, with a fresh, rolled state, or null
     * if the id isn't a jewelry item
     */
    @Nullable
    public static ItemStack create(String itemId, @Nullable String cut) {
        JewelryDefinition definition = JewelryDefinition.get(itemId);
        if (definition == null) {
            return null;
        }
        // a named piece's own cut, unless the loot entry picked one
        GemCut gemCut = definition.getGem() == null ? null
                : GemCut.byName(cut != null ? cut : definition.getCut());
        JewelryStats stats = JewelryStats.of(definition, gemCut);
        JewelryState state = JewelryState.fresh(stats);
        if (gemCut != null) {
            state.setCut(gemCut.getName());
        }
        return withState(new ItemStack(itemId, 1), state);
    }

    /**
     * @return a copy of the stack carrying this state and a tooltip that shows it
     */
    public static ItemStack withState(ItemStack stack, JewelryState state) {
        // a piece's elemental properties are rolled the first time its state is written
        state = ElementRolling.ensureRolled(definition(stack), state);
        JewelryStats stats = JewelryStats.of(definition(stack), GemCut.byName(state.getCut()));
        // keep a name the stack was given (a named piece), and refresh the description
        ItemDisplayMetadata old = stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
        ItemDisplayMetadata display = new ItemDisplayMetadata(old == null ? null : old.getName(),
                describe(stats, state));
        return stack.withMetadata(JewelryState.KEYED_CODEC, state)
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, display);
    }

    /**
     * The tooltip text for a piece: mana, uses, recharges and repairs, spell level, rune
     * sockets, and its spells and runes.
     */
    public static Message describe(JewelryStats stats, JewelryState state) {
        // every word is a translation key, magictreasures.tooltip.* (v1.2)
        if (!stats.known()) {
            return Lang.of("tooltip.unknown").color(LABEL);
        }
        List<Message> parts = new ArrayList<>();
        JewelryDefinition definition = stats.definition();
        if (definition.isNamed()) {
            // a named piece's lore, from server.lang (items.<id>.lore), Nov 13
            parts.add(Message.translation("server.items." + definition.getId() + ".lore").color(LORE));
            parts.add(Message.raw("\n"));
        }
        // "Charge", not "Mana": the piece's own well, apart from the player's mana bar
        if (stats.gem() != null) {
            GemCut cut = GemCut.byName(state.getCut());
            // the cut gem's own item name, "Fine Ruby" ("Rubi Fino")
            line(parts, "gem", Lang.item((cut == null ? GemCut.FINE : cut).itemId(stats.gem())), VALUE);
        }
        if (definition.isUnlimited()) {
            line(parts, "charge", Lang.of("tooltip.unlimited"), MANA);
        } else {
            line(parts, "charge", String.format("%.0f / %.0f", state.getMana(), stats.maxMana()), MANA);
        }
        if (definition.isUnbreakable()) {
            line(parts, "uses", Lang.of("tooltip.unbreakable"), VALUE);
        } else {
            line(parts, "uses", state.getUses() + " / " + stats.maxUses(), VALUE);
        }
        if (!definition.isUnlimited()) {
            line(parts, "recharges", state.getRecharges() + " / " + stats.maxRecharges(), VALUE);
        }
        if (!definition.isUnbreakable()) {
            line(parts, "repairs", state.getRepairs() + " / " + stats.maxRepairs(), VALUE);
        }
        line(parts, "spellLevel", Lang.of("tooltip.upTo").param("level", stats.maxLevel()), VALUE);
        line(parts, "runeSockets", state.getRunes().length + " / " + stats.sockets(), VALUE);
        if (state.getSpells().length == 0) {
            line(parts, "spells", Lang.of("tooltip.none"), VALUE);
        } else {
            spells(parts, stats, state);
        }
        if (state.getRunes().length > 0) {
            // the runes' own names (server.items.Rune_Enrage.name is "Enrage"), not their ids
            // (Mark, Nov 16: the tooltip read "Rune_Enrage")
            parts.add(Message.join(Lang.of("tooltip.runes"), Message.raw(": ")).color(LABEL));
            for (int i = 0; i < state.getRunes().length; i++) {
                if (i > 0) {
                    parts.add(Message.raw(", ").color(VALUE));
                }
                parts.add(Message.translation("server.items." + state.getRunes()[i] + ".name").color(VALUE));
            }
            parts.add(Message.raw("\n"));
        }
        for (List<ElementProperty> group : forTooltip(ElementRolling.decode(state))) {
            ElementProperty total = total(group);
            boolean several = group.size() > 1;
            Message source = several ? null : source(group.getFirst());
            parts.add((source == null ? total.describe() : Message.join(total.describe(), source))
                    .color(total.good() ? GOOD : BAD));
            parts.add(Message.raw("\n"));
            Message breakdown = several ? breakdown(group) : null;
            if (breakdown != null) {
                // on its own line, like a spell's breakdown: on one line with the total it
                // wrapped the tooltip (Mark, Nov 16)
                parts.add(Message.join(Message.raw("    ("), breakdown, Message.raw(")")).color(LABEL));
                parts.add(Message.raw("\n"));
            }
        }
        for (String curse : definition.getCurses()) {
            parts.add(curseText(curse).color(BAD));
            parts.add(Message.raw("\n"));
        }
        // drop the trailing newline
        parts.removeLast();
        return Message.empty().insertAll(parts);
    }

    /**
     * @return a curse's tooltip line, for example "Curse of Vanishing: destroyed when you die"
     */
    public static Message curseText(String curse) {
        if (JewelryDefinition.CURSE_VANISHING.equalsIgnoreCase(curse)) {
            return Lang.of("tooltip.curseVanishing");
        }
        return Lang.of("tooltip.curse").param("curse", curse);
    }

    /**
     * One line per spell with what it does on this piece, a line of where the amount comes
     * from, then a line of its cost, cooldown and the like (Nov 14), for example:
     * <pre>
     * Vampiric Embrace (6): drains 25 on hit
     *    (20, +25% Bone)
     *    cooldown 7.5 s
     * </pre>
     */
    private static void spells(List<Message> parts, JewelryStats stats, JewelryState state) {
        parts.add(Message.join(Lang.of("tooltip.spells"), Message.raw(":")).color(LABEL));
        parts.add(Message.raw("\n"));
        GemCut cut = GemCut.byName(state.getCut());
        for (String id : state.getSpells()) {
            SpellTooltip spell = SpellTooltip.of(id, stats, cut);
            if (spell == null) {
                parts.add(Message.raw(" " + id).color(VALUE));
                parts.add(Message.raw("\n"));
                continue;
            }
            parts.add(Message.join(Message.raw(" "), spell.name(),
                    Message.raw(spell.summary() == null ? "" : ": ")).color(VALUE));
            if (spell.summary() != null) {
                parts.add(spell.summary().color(GOOD));
            }
            parts.add(Message.raw("\n"));
            // on its own line, so it doesn't wrap mid-way (Nov 15)
            if (spell.breakdown() != null) {
                parts.add(Message.join(Message.raw("    ("), spell.breakdown(), Message.raw(")")).color(LABEL));
                parts.add(Message.raw("\n"));
            }
            for (Message detail : spell.details()) {
                parts.add(Message.join(Message.raw("    "), detail).color(LABEL));
                parts.add(Message.raw("\n"));
            }
        }
    }

    /**
     * A rune's name for plain text (chat, the panel), from its id: "Rune_GroundSlam" becomes
     * "Ground Slam". Where a {@link Message} can be used, prefer the rune's own translated name
     * ({@code server.items.<id>.name}), as the tooltip does; a few vanilla names differ from
     * their ids (Rune_PoisonImbue is "Imbue Poison").
     */
    public static String runeName(String runeId) {
        String bare = runeId.startsWith("Rune_") ? runeId.substring("Rune_".length()) : runeId;
        return bare.replace('_', ' ').replaceAll("(?<=[a-z])(?=[A-Z])", " ");
    }

    /**
     * The piece's name as players see it: a name the stack was given, if any, or else its
     * item's own (translated) name. Named jewelry gets its name either way, so use this for
     * anything shown on screen (the client translates it).
     */
    public static Message name(ItemStack stack) {
        ItemDisplayMetadata display = stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
        if (display != null && display.getName() != null) {
            return display.getName();
        }
        Item item = stack.getItem();
        return item != null ? item.getTranslationMessage() : Message.raw(displayName(stack));
    }

    /**
     * @return a plain name built from the piece's type, material, gem and style, for example
     * {@code Great Gold Ruby Ring} (for chat and logs; on-screen text uses {@link #name}); the
     * item id if it isn't jewelry
     */
    public static String displayName(ItemStack stack) {
        JewelryDefinition definition = definition(stack);
        if (definition == null) {
            return stack == null ? "" : stack.getItemId();
        }
        if (definition.getName() != null) {
            return definition.getName();
        }
        JewelryStyle style = JewelryStyle.get(definition.getStyle());
        StringBuilder name = new StringBuilder();
        if (style != null && !"Regular".equalsIgnoreCase(style.getId())) {
            name.append(style.getName()).append(' ');
        }
        name.append(definition.getMaterial()).append(' ');
        if (definition.getGem() != null) {
            name.append(definition.getGem()).append(' ');
        }
        return name.append(definition.getType()).toString();
    }

    /**
     * The piece's element properties as the tooltip shows them, in groups: resistances and crit
     * chances of the same element are one group, shown as one line with their total, as they add
     * up in play ({@code ElementWear} sums them). Element damage and statuses on hit are a group
     * each. (Mark, Nov 16: a silver emerald piece showed "Poison resistance +14%" and "+7%" as two
     * lines; it now reads "Poison resistance +21% (silver 14%, emerald 7%)".) Display only: the
     * saved properties are unchanged.
     */
    private static List<List<ElementProperty>> forTooltip(List<ElementProperty> properties) {
        List<List<ElementProperty>> out = new ArrayList<>();
        for (ElementProperty property : properties) {
            boolean adds = property.kind() == ElementProperty.Kind.RESISTANCE
                    || property.kind() == ElementProperty.Kind.CRIT;
            List<ElementProperty> same = null;
            for (int i = 0; adds && i < out.size(); i++) {
                ElementProperty seen = out.get(i).getFirst();
                if (seen.kind() == property.kind() && seen.element().equals(property.element())) {
                    same = out.get(i);
                }
            }
            if (same == null) {
                same = new ArrayList<>();
                out.add(same);
            }
            same.add(property);
        }
        return out;
    }

    /**
     * @return the group as one property: its value the sum of the parts as the tooltip rounds
     *         them, so the total always matches the breakdown (14% + 7% reads 21%)
     */
    private static ElementProperty total(List<ElementProperty> group) {
        ElementProperty first = group.getFirst();
        if (group.size() == 1) {
            return first;
        }
        long percent = 0;
        for (ElementProperty part : group) {
            percent += Math.round(part.value() * 100);
        }
        return new ElementProperty(first.kind(), first.element(), percent / 100.0, first.effect());
    }

    /**
     * @return a lone power's source after its line, " (ruby)"; empty for a piece rolled before
     *         v1.1, which didn't save its sources
     */
    @Nullable
    private static Message source(ElementProperty property) {
        return property.source() == null ? null
                : Message.join(Message.raw(" ("), Lang.source(property.source()), Message.raw(")"));
    }

    /**
     * @return what makes up a group's total, "silver 14%, emerald 7%", shown on the line under
     *         it; null for a piece rolled before v1.1, which didn't save its sources
     */
    @Nullable
    private static Message breakdown(List<ElementProperty> group) {
        if (group.stream().allMatch(part -> part.source() == null)) {
            return null;
        }
        List<Message> bits = new ArrayList<>();
        for (ElementProperty part : group) {
            Message percent = Message.raw(Math.round(part.value() * 100) + "%");
            bits.add(part.source() == null ? percent
                    : Message.join(Lang.source(part.source()), Message.raw(" "), percent));
        }
        return Lang.join(bits, ", ");
    }

    /** A "Label: value" line; the label is {@code magictreasures.tooltip.<label>}. */
    private static void line(List<Message> parts, String label, String value, String color) {
        line(parts, label, Message.raw(value), color);
    }

    private static void line(List<Message> parts, String label, Message value, String color) {
        parts.add(Message.join(Lang.of("tooltip." + label), Message.raw(": ")).color(LABEL));
        parts.add(value.color(color));
        parts.add(Message.raw("\n"));
    }
}
