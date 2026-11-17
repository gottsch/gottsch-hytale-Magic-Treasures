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
package mod.gottsch.hytale.magictreasures.core.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.element.ElementProperty;
import mod.gottsch.hytale.magictreasures.core.element.ElementWear;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.spell.SpellEngine;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import mod.gottsch.hytale.magictreasures.core.wear.JewelryWear;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * /mt worn: lists the pieces the player wears, each with its charge, uses, runes and the
 * seconds until each spell is ready, then the totals of their worn element powers. Open to
 * every player, so it reads like the worn-jewelry panel, not a debug dump (Nov 15). If the
 * plugin's record of what is worn has fallen behind the inventory, it quietly sends the
 * player's look again.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class WornCommand extends AbstractPlayerCommand {

    public WornCommand() {
        super("worn", "Show the jewelry you are wearing.");
        // open to every player (Nov 15, release)
        this.setPermissionGroups(MagicTreasuresCommand.PERMISSION_EVERYONE);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        WornJewelry worn = WornJewelry.of(ref, store);
        if (!JewelryWear.worn(playerRef.getUuid()).itemIds().equals(worn.itemIds())) {
            // the record missed an inventory change: catch it up before reading the totals
            JewelryWear.update(ref, store, true);
        }
        if (worn.caseLocation() == null) {
            context.sendMessage(Message.raw("You have no Treasures Case, so nothing is worn."));
            return;
        }
        if (worn.pieces().isEmpty()) {
            context.sendMessage(Message.raw("Your Treasures Case is empty."));
            return;
        }
        int count = worn.pieces().size();
        context.sendMessage(Message.raw("You're wearing " + count + (count == 1 ? " piece:" : " pieces:")));
        for (int i = 0; i < count; i++) {
            ItemStack piece = worn.pieces().get(i);
            short slot = worn.pieceSlots().get(i);
            context.sendMessage(Message.join(Message.raw("  "), Jewelry.name(piece),
                    Message.raw(": " + condition(piece)).color("#c8c8c8")));
            String spells = spells(playerRef, slot, Jewelry.state(piece));
            if (!spells.isEmpty()) {
                context.sendMessage(Message.raw("    " + spells).color("#9fd3ff"));
            }
        }
        // the worn element powers, added up per kind and element (Nov 11)
        Map<String, Double> totals = new TreeMap<>();
        Map<String, ElementProperty> examples = new HashMap<>();
        for (ElementProperty property : ElementWear.properties(playerRef.getUuid())) {
            String key = property.kind() + ":" + property.element() + ":" + property.effect();
            totals.merge(key, property.value(), Double::sum);
            examples.putIfAbsent(key, property);
        }
        if (!totals.isEmpty()) {
            context.sendMessage(Message.raw("Element powers (resistance held within "
                    + Math.round(ElementWear.RESISTANCE_CAP * 100) + "%):"));
            totals.forEach((key, value) -> {
                ElementProperty e = examples.get(key);
                ElementProperty sum = new ElementProperty(e.kind(), e.element(), value, e.effect());
                context.sendMessage(Message.raw("  " + sum.describe()).color(sum.good() ? "#7ddc7d" : "#ff7070"));
            });
        }
    }

    /**
     * @return for example "charge 40/60, uses 120/300"
     */
    private static String condition(ItemStack piece) {
        JewelryStats stats = Jewelry.stats(piece);
        JewelryState state = Jewelry.state(piece);
        if (state == null || !stats.known()) {
            return "unknown";
        }
        JewelryDefinition definition = stats.definition();
        String charge = definition.isUnlimited() ? "charge unlimited"
                : String.format("charge %.0f/%.0f", state.getMana(), stats.maxMana());
        String uses = definition.isUnbreakable() ? "unbreakable"
                : "uses " + state.getUses() + "/" + stats.maxUses();
        return charge + ", " + uses;
    }

    /**
     * @return the piece's spells with the seconds until each is ready, then its runes, for
     * example "Healing ready, Speed in 42 s, Fireball rune"; empty if it has neither
     */
    private static String spells(PlayerRef playerRef, short slot, @Nullable JewelryState state) {
        if (state == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String id : state.getSpells()) {
            Spell spell = Spell.get(id);
            String name = spell == null ? id : spell.getName();
            double left = SpellEngine.cooldownLeft(playerRef.getUuid(), slot, id);
            parts.add(name + (left > 0 ? " in " + (int) Math.ceil(left) + " s" : " ready"));
        }
        for (String rune : state.getRunes()) {
            parts.add(Jewelry.runeName(rune) + " rune");
        }
        return String.join(", ", parts);
    }
}
