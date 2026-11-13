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
package mod.gottsch.hytale.magictreasures.core.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.spell.SpellEngine;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import java.util.ArrayList;
import java.util.List;

/**
 * The worn-jewelry HUD: one row per worn piece with its name, its charge (the jewelry's own
 * mana, called "Charge" so it isn't mistaken for the player's mana bar), a bar, and its
 * spells with the seconds until each is ready.
 * <p>
 * Built like Mage Flame's lifespan HUD: inline markup with a hidden row per slot, then
 * {@link #update} shows, hides and re-texts rows, and sends nothing when nothing changed.
 * A player gets the HUD the first time they wear jewelry, so players who never do are left
 * alone. It is added again whenever it is missing (after respawning).
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class JewelryHud extends CustomUIHud {
    public static final String KEY = "MagicTreasuresHud";

    /**
     * Position on screen, in UI pixels. Was Top 330, below Mage Flame's HUD (Top 120); Mark
     * (Nov 10): start higher in the view. Top 70 is just under the compass.
     */
    private static final String ANCHOR = "(Left: 20, Top: 70, Width: 360)";

    private static final int ROWS = 4;

    private String lastSignature = "";

    public JewelryHud(PlayerRef playerRef) {
        super(playerRef, KEY);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        StringBuilder markup = new StringBuilder("Group #MagicTreasuresHud {\n  Anchor: " + ANCHOR + ";\n  LayoutMode: Top;\n");
        for (int i = 1; i <= ROWS; i++) {
            markup.append("""
                      Group #JewelRow%1$d {
                        Visible: false;
                        LayoutMode: Top;
                        Anchor: (Bottom: 6, Height: 62);
                        Background: #000000(0.35);
                        Padding: (Horizontal: 10, Vertical: 4);

                        Group {
                          LayoutMode: Left;
                          Anchor: (Height: 20);

                          Label #JewelName%1$d {
                            Anchor: (Width: 210);
                            Text: "";
                            Style: (FontSize: 14, TextColor: #e8c96a, VerticalAlignment: Center);
                          }

                          Label #JewelCharge%1$d {
                            Text: "";
                            Style: (FontSize: 14, TextColor: #8fc8ff, RenderBold: true, VerticalAlignment: Center);
                          }
                        }

                        Group {
                          Anchor: (Top: 2, Height: 6);
                          Background: #1b1b24(0.9);

                          ProgressBar #JewelBar%1$d {
                            Value: 1.0;
                            BarTexturePath: "Common/ProgressBarFill.png";
                          }
                        }

                        Label #JewelSpells%1$d {
                          Anchor: (Top: 3, Height: 18);
                          Text: "";
                          Style: (FontSize: 12, TextColor: #d0d0d0, VerticalAlignment: Center);
                        }
                      }
                    """.formatted(i));
        }
        markup.append("}\n");
        builder.appendInline(null, markup.toString());
    }

    /**
     * Adds the HUD if the player wears jewelry and doesn't have it yet, then shows what they
     * wear. Call on the world thread.
     */
    public static void refresh(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, WornJewelry worn) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        HudManager hudManager = player.getHudManager();
        CustomUIHud existing = hudManager.getCustomHud(KEY);
        JewelryHud hud;
        if (existing instanceof JewelryHud jewelryHud) {
            hud = jewelryHud;
        } else if (!worn.pieces().isEmpty()) {
            hud = new JewelryHud(playerRef);
            hudManager.addCustomHud(playerRef, hud);
        } else {
            return;
        }
        hud.show(playerRef, worn);
    }

    private void show(PlayerRef playerRef, WornJewelry worn) {
        UICommandBuilder builder = new UICommandBuilder();
        StringBuilder signature = new StringBuilder();
        for (int i = 1; i <= ROWS; i++) {
            boolean visible = i <= worn.pieces().size();
            builder.set("#JewelRow" + i + ".Visible", visible);
            if (!visible) {
                signature.append(i).append("-|");
                continue;
            }
            ItemStack piece = worn.pieces().get(i - 1);
            short slot = worn.pieceSlots().get(i - 1);
            JewelryStats stats = Jewelry.stats(piece);
            JewelryState state = Jewelry.state(piece);
            double mana = state == null ? 0 : state.getMana();
            // the item's own name (named jewelry shows its name), translated by the client
            String name = piece.getItemId();
            boolean unlimited = stats.definition() != null && stats.definition().isUnlimited();
            String charge = unlimited ? "Charge unlimited" : String.format("Charge %.0f/%.0f", mana, stats.maxMana());
            float share = unlimited ? 1f
                    : stats.maxMana() <= 0 ? 0f : (float) Math.clamp(mana / stats.maxMana(), 0.0, 1.0);
            String spells = spells(playerRef, slot, state);
            builder.set("#JewelName" + i + ".TextSpans", Jewelry.name(piece));
            builder.set("#JewelCharge" + i + ".Text", charge);
            builder.set("#JewelBar" + i + ".Value", share);
            builder.set("#JewelSpells" + i + ".Text", spells);
            signature.append(i).append(name).append(charge).append(spells).append('|');
        }
        if (signature.toString().equals(lastSignature)) {
            return;
        }
        lastSignature = signature.toString();
        update(false, builder);
    }

    /**
     * @return the piece's spells with the seconds until each is ready, for example
     * {@code Healing ready, Speed 42s}
     */
    private static String spells(PlayerRef playerRef, short slot, JewelryState state) {
        if (state == null || (state.getSpells().length == 0 && state.getRunes().length == 0)) {
            return "No spells";
        }
        List<String> parts = new ArrayList<>();
        // socketed runes first: they boost the same rune in the ability slots
        for (String rune : state.getRunes()) {
            parts.add("+" + rune.replace("Rune_", ""));
        }
        for (String id : state.getSpells()) {
            Spell spell = Spell.get(id);
            String name = spell == null ? id + " (unknown)" : spell.getName();
            double left = SpellEngine.cooldownLeft(playerRef.getUuid(), slot, id);
            parts.add(name + (left > 0 ? " " + (int) Math.ceil(left) + "s" : " ready"));
        }
        // a row fits about 50 characters; past that the label cuts off with "..."
        return String.join(", ", parts).replace("Quick ", "Q. ").replace("Greater ", "Gr. ").replace("Lesser ", "L. ");
    }
}
