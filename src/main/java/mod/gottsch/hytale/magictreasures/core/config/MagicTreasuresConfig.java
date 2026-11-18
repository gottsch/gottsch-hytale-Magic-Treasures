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
package mod.gottsch.hytale.magictreasures.core.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Server settings, saved as {@code MagicTreasures.json} in the plugin's data folder. The file
 * is written with the defaults on the first start; a server owner edits it and restarts.
 * <p>
 * Every setting is a multiplier, 1 being the mod as shipped, so a server can make the mod
 * richer or leaner without editing its assets (v1.2, Nov 18):
 * <ul>
 *     <li>{@code ChestLootChance}, {@code MobLootChance}, {@code BossLootChance}: multiply the
 *     chance that a chest, a monster or a boss gets anything from Magic Treasures (2 doubles it,
 *     0 turns it off). The result is capped at 100%.</li>
 *     <li>{@code RefillSpeed}: how fast worn pieces refill their charge (2 is twice as fast,
 *     0 turns refilling off, so only a recharge refills a piece).</li>
 *     <li>{@code SpellCooldowns}: multiplies every spell's cooldown (0.5 halves it). Tooltips
 *     show the result.</li>
 * </ul>
 * Negative values count as 0.
 *
 * @author Mark Gottschling on 11/18/2026
 */
public class MagicTreasuresConfig {

    public static final BuilderCodec<MagicTreasuresConfig> CODEC = BuilderCodec.builder(
                    MagicTreasuresConfig.class, MagicTreasuresConfig::new)
            .append(new KeyedCodec<>("ChestLootChance", Codec.DOUBLE),
                    (c, v, extra) -> c.chestLootChance = v, (c, extra) -> c.chestLootChance).add()
            .append(new KeyedCodec<>("MobLootChance", Codec.DOUBLE),
                    (c, v, extra) -> c.mobLootChance = v, (c, extra) -> c.mobLootChance).add()
            .append(new KeyedCodec<>("BossLootChance", Codec.DOUBLE),
                    (c, v, extra) -> c.bossLootChance = v, (c, extra) -> c.bossLootChance).add()
            .append(new KeyedCodec<>("RefillSpeed", Codec.DOUBLE),
                    (c, v, extra) -> c.refillSpeed = v, (c, extra) -> c.refillSpeed).add()
            .append(new KeyedCodec<>("SpellCooldowns", Codec.DOUBLE),
                    (c, v, extra) -> c.spellCooldowns = v, (c, extra) -> c.spellCooldowns).add()
            .build();

    /** The settings in use: the defaults until the plugin loads the file. */
    private static MagicTreasuresConfig current = new MagicTreasuresConfig();

    private double chestLootChance = 1;
    private double mobLootChance = 1;
    private double bossLootChance = 1;
    private double refillSpeed = 1;
    private double spellCooldowns = 1;

    private MagicTreasuresConfig() {
    }

    public static MagicTreasuresConfig get() {
        return current;
    }

    /**
     * Makes these the settings in use (the plugin calls it once the file is loaded).
     */
    public static void use(MagicTreasuresConfig config) {
        if (config != null) {
            current = config;
        }
    }

    public double getChestLootChance() {
        return Math.max(0, chestLootChance);
    }

    public double getMobLootChance() {
        return Math.max(0, mobLootChance);
    }

    public double getBossLootChance() {
        return Math.max(0, bossLootChance);
    }

    public double getRefillSpeed() {
        return Math.max(0, refillSpeed);
    }

    public double getSpellCooldowns() {
        return Math.max(0, spellCooldowns);
    }
}
