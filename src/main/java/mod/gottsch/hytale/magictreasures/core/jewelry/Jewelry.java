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
        return JewelryStats.of(definition, state == null ? null : GemCut.byName(state.getCut()));
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
            return JewelryState.fresh(JewelryStats.of(definition));
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
        if (!stats.known()) {
            return Message.raw("This jewelry's material or style is missing. It can't cast.").color(LABEL);
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
            line(parts, "Gem", (cut == null ? GemCut.FINE : cut).getName() + " " + stats.gem().getId().toLowerCase(),
                    VALUE);
        }
        if (definition.isUnlimited()) {
            line(parts, "Charge", "unlimited", MANA);
        } else {
            line(parts, "Charge", String.format("%.0f / %.0f", state.getMana(), stats.maxMana()), MANA);
        }
        if (definition.isUnbreakable()) {
            line(parts, "Uses", "unbreakable", VALUE);
        } else {
            line(parts, "Uses", state.getUses() + " / " + stats.maxUses(), VALUE);
        }
        if (!definition.isUnlimited()) {
            line(parts, "Recharges", state.getRecharges() + " / " + stats.maxRecharges(), VALUE);
        }
        if (!definition.isUnbreakable()) {
            line(parts, "Repairs", state.getRepairs() + " / " + stats.maxRepairs(), VALUE);
        }
        line(parts, "Spell level", "up to " + stats.maxLevel(), VALUE);
        line(parts, "Rune sockets", state.getRunes().length + " / " + stats.sockets(), VALUE);
        line(parts, "Spells", state.getSpells().length == 0 ? "none" : spellNames(state.getSpells()), VALUE);
        if (state.getRunes().length > 0) {
            line(parts, "Runes", String.join(", ", state.getRunes()), VALUE);
        }
        for (ElementProperty property : ElementRolling.decode(state)) {
            parts.add(Message.raw(property.describe()).color(property.good() ? GOOD : BAD));
            parts.add(Message.raw("\n"));
        }
        for (String curse : definition.getCurses()) {
            parts.add(Message.raw(curseText(curse)).color(BAD));
            parts.add(Message.raw("\n"));
        }
        // drop the trailing newline
        parts.removeLast();
        return Message.empty().insertAll(parts);
    }

    /**
     * @return a curse's tooltip line, for example "Curse of Vanishing: destroyed when you die"
     */
    public static String curseText(String curse) {
        if (JewelryDefinition.CURSE_VANISHING.equalsIgnoreCase(curse)) {
            return "Curse of Vanishing: destroyed when you die";
        }
        return "Curse of " + curse;
    }

    private static String spellNames(String[] ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            Spell spell = Spell.get(id);
            names.add(spell == null ? id : spell.getName() + " (" + spell.getLevel() + ")");
        }
        return String.join(", ", names);
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

    private static void line(List<Message> parts, String label, String value, String color) {
        parts.add(Message.raw(label + ": ").color(LABEL));
        parts.add(Message.raw(value).color(color));
        parts.add(Message.raw("\n"));
    }
}
