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
import mod.gottsch.hytale.magictreasures.core.lang.Lang;
import com.hypixel.hytale.server.core.Message;

/**
 * The worn-jewelry HUD: one row per worn piece with its name, its charge (the jewelry's own
 * mana, called "Charge" so it isn't mistaken for the player's mana bar), a bar, and its
 * spells with the seconds until each is ready.
 * <p>
 * Built like Mage Flame's lifespan HUD: inline markup with a hidden row per slot, then
 * {@link #update} shows, hides and re-texts rows, and sends nothing when nothing changed.
 * A player gets the HUD the first time they wear jewelry, so players who never do are left
 * alone. It is added again whenever it is missing (after respawning).
 * <p>
 * Cooldowns show as seconds ({@code Healing 42s}) or, if the player chose bars on the Treasures
 * Case page ({@link HudSettings}, v1.3), as a thin red bar per spell under the spell names, full
 * when the spell is cast and emptying as its cooldown runs down (Mark, Nov 19). The mode is fixed when the HUD is built: switching rebuilds it.
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
    /** Cooldown bars per row: a piece holds at most two spells (Starfall). */
    private static final int BARS = 2;
    /** The cooldown bars' fill, red so they don't read as the charge bar. */
    private static final String BAR_FILL = "MagicTreasures/CooldownBarFill.png";

    private final boolean cooldownBars;
    private String lastSignature = "";

    public JewelryHud(PlayerRef playerRef, boolean cooldownBars) {
        super(playerRef, KEY);
        this.cooldownBars = cooldownBars;
    }

    @Override
    protected void build(UICommandBuilder builder) {
        StringBuilder markup = new StringBuilder("Group #MagicTreasuresHud {\n  Anchor: " + ANCHOR + ";\n  LayoutMode: Top;\n");
        for (int i = 1; i <= ROWS; i++) {
            markup.append("""
                      Group #JewelRow%1$d {
                        Visible: false;
                        LayoutMode: Top;
                        Anchor: (Bottom: 6, Height: %2$d);
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
                    """.formatted(i, cooldownBars ? 62 + 6 * BARS : 62));
            if (cooldownBars) {
                for (int b = 1; b <= BARS; b++) {
                    markup.append("""
                              Group #JewelCdBox%1$d%2$d {
                                Anchor: (Top: 2, Height: 4);
                                Background: #1b1b24(0.9);

                                ProgressBar #JewelCd%1$d%2$d {
                                  Value: 0.0;
                                  BarTexturePath: "%3$s";
                                }
                              }
                            """.formatted(i, b, BAR_FILL));
                }
            }
            markup.append("  }\n");
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
        boolean bars = HudSettings.cooldownBars(ref, store);
        JewelryHud hud;
        if (existing instanceof JewelryHud jewelryHud && jewelryHud.cooldownBars == bars) {
            hud = jewelryHud;
        } else if (existing instanceof JewelryHud || !worn.pieces().isEmpty()) {
            // new, or the player switched how cooldowns show: build it again in that mode
            if (existing != null) {
                hudManager.removeCustomHud(playerRef, KEY);
            }
            hud = new JewelryHud(playerRef, bars);
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
            String chargeText = unlimited ? "unlimited" : String.format("%.0f/%.0f", mana, stats.maxMana());
            Message charge = unlimited ? Lang.of("hud.chargeUnlimited") : Lang.of("hud.charge").param("charge", chargeText);
            float share = unlimited ? 1f
                    : stats.maxMana() <= 0 ? 0f : (float) Math.clamp(mana / stats.maxMana(), 0.0, 1.0);
            StringBuilder spellSignature = new StringBuilder();
            Message spells = spells(playerRef, slot, state, spellSignature, cooldownBars);
            if (cooldownBars) {
                bars(builder, i, playerRef, slot, state, spellSignature);
            }
            builder.set("#JewelName" + i + ".TextSpans", Jewelry.name(piece));
            builder.set("#JewelCharge" + i + ".TextSpans", charge);
            builder.set("#JewelBar" + i + ".Value", share);
            builder.set("#JewelSpells" + i + ".TextSpans", spells);
            signature.append(i).append(name).append(chargeText).append(spellSignature).append('|');
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
    private static Message spells(PlayerRef playerRef, short slot, JewelryState state, StringBuilder signature,
                                  boolean bars) {
        if (state == null || (state.getSpells().length == 0 && state.getRunes().length == 0)) {
            return Lang.of("hud.noSpells");
        }
        List<Message> parts = new ArrayList<>();
        // socketed runes first: they boost the same rune in the ability slots
        for (String rune : state.getRunes()) {
            parts.add(Message.join(Message.raw("+"), Lang.item(rune)));
            signature.append(rune).append(',');
        }
        // translated (v1.2), so no longer shortened ("Gr. Healing"): a piece holds one spell,
        // which fits the row's 50 or so characters with a rune or two
        for (String id : state.getSpells()) {
            Message name = Spell.get(id) == null ? Message.raw(id) : Lang.spell(id);
            int left = (int) Math.ceil(SpellEngine.cooldownLeft(playerRef.getUuid(), slot, id));
            // with bars, the bar shows the wait: just the name until it's ready
            parts.add(left <= 0 ? Lang.of("hud.spellReady").param("spell", name)
                    : bars ? name : Lang.of("hud.spellIn").param("spell", name).param("seconds", left));
            signature.append(id).append(':').append(Math.max(0, left)).append(',');
        }
        return Lang.join(parts, ", ");
    }

    /**
     * Sets the row's cooldown bars, one per spell in the spell names' order, and hides the
     * rest. A bar is full when its spell was just cast and empty when it is ready.
     */
    private static void bars(UICommandBuilder builder, int row, PlayerRef playerRef, short slot,
                             JewelryState state, StringBuilder signature) {
        String[] spells = state == null ? new String[0] : state.getSpells();
        for (int b = 1; b <= BARS; b++) {
            boolean visible = b <= spells.length;
            builder.set("#JewelCdBox" + row + b + ".Visible", visible);
            if (visible) {
                float remaining = SpellEngine.cooldownRemaining(playerRef.getUuid(), slot, spells[b - 1]);
                builder.set("#JewelCd" + row + b + ".Value", remaining);
                // in twentieths, so the HUD resends as the bar moves, not every tick
                signature.append('b').append(Math.round(remaining * 20));
            }
        }
    }
}
