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

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;

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

    public static JewelryStats stats(@Nullable ItemStack stack) {
        return JewelryStats.of(definition(stack));
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
        return state != null ? state : JewelryState.fresh(JewelryStats.of(definition));
    }

    /**
     * @return a new stack of the jewelry item with this id, with a fresh state and tooltip,
     * or null if the id isn't a jewelry item
     */
    @Nullable
    public static ItemStack create(String itemId) {
        JewelryDefinition definition = JewelryDefinition.get(itemId);
        if (definition == null) {
            return null;
        }
        JewelryStats stats = JewelryStats.of(definition);
        return withState(new ItemStack(itemId, 1), JewelryState.fresh(stats));
    }

    /**
     * @return a copy of the stack carrying this state and a tooltip that shows it
     */
    public static ItemStack withState(ItemStack stack, JewelryState state) {
        JewelryStats stats = stats(stack);
        ItemDisplayMetadata display = new ItemDisplayMetadata(null, describe(stats, state));
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
        line(parts, "Mana", String.format("%.0f / %.0f", state.getMana(), stats.maxMana()), MANA);
        line(parts, "Uses", state.getUses() + " / " + stats.maxUses(), VALUE);
        line(parts, "Recharges", state.getRecharges() + " / " + stats.maxRecharges(), VALUE);
        line(parts, "Repairs", state.getRepairs() + " / " + stats.maxRepairs(), VALUE);
        line(parts, "Spell level", "up to " + stats.maxLevel(), VALUE);
        line(parts, "Rune sockets", state.getRunes().length + " / " + stats.sockets(), VALUE);
        line(parts, "Spells", state.getSpells().length == 0 ? "none" : String.join(", ", state.getSpells()), VALUE);
        if (state.getRunes().length > 0) {
            line(parts, "Runes", String.join(", ", state.getRunes()), VALUE);
        }
        // drop the trailing newline
        parts.removeLast();
        return Message.empty().insertAll(parts);
    }

    private static void line(List<Message> parts, String label, String value, String color) {
        parts.add(Message.raw(label + ": ").color(LABEL));
        parts.add(Message.raw(value).color(color));
        parts.add(Message.raw("\n"));
    }
}
