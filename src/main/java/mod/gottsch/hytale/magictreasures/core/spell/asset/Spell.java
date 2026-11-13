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
package mod.gottsch.hytale.magictreasures.core.spell.asset;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellOverrides;
import mod.gottsch.hytale.magictreasures.core.spell.SpellKind;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Arrays;

/**
 * A passive spell: a Magic Treasures JSON asset under {@code Server/MagicTreasures/Spells/}.
 * The file name is the spell's id, which is what a piece of jewelry stores.
 * <p>
 * {@code Kind} picks the code that runs it ({@link SpellKind}). The numbers are in Hytale
 * units, not Minecraft's: health is about 5 times larger, and times are in seconds.
 * <ul>
 *     <li>{@code Level}: the spell level. A piece can hold spells up to its max level.</li>
 *     <li>{@code Cost}: jewelry mana spent per cast.</li>
 *     <li>{@code Cooldown}: seconds before the same piece can cast it again.</li>
 *     <li>{@code Effect}: health healed (a Vampiric spell drains it from the target first); for
 *     a Weapon spell, the share of each
 *     weapon hit added as {@code Element} damage (0.25: 25% of the hit, Nov 12).</li>
 *     <li>{@code Element}: a Weapon spell's element (a damage cause id such as {@code Fire}),
 *     or the elements a Ward starts on, comma separated ({@code "Fire, Poison"}).</li>
 *     <li>{@code Duration}: seconds a buff lasts.</li>
 *     <li>{@code EntityEffect}: the effect asset a buff applies (speed, strength, and the
 *     status icon).</li>
 *     <li>{@code OnHit}: a weapon spell's status effect, given to the target on every melee or
 *     ranged hit while the spell's effect runs (Nov 11).</li>
 *     <li>{@code OnHitChance}: the chance per hit of giving {@code OnHit}, 0 to 1 (default 1,
 *     every hit). Stun and Root use a low chance, so a mob isn't held for good (Nov 12).</li>
 *     <li>{@code Pulse}, {@code MaxTargets}, {@code Radius}: a Drain aura takes {@code Effect}
 *     health from up to {@code MaxTargets} hostile mobs within {@code Radius} blocks, every
 *     {@code Pulse} seconds while it lasts (Nov 13).</li>
 * </ul>
 * The wearing piece's factors scale cost, cooldown, effect, duration and (for an aura's
 * {@code Radius}) range when it casts.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class Spell implements JsonAssetWithMap<String, DefaultAssetMap<String, Spell>> {
    public static final AssetBuilderCodec<String, Spell> CODEC = AssetBuilderCodec.builder(
                    Spell.class, Spell::new, Codec.STRING,
                    (a, id) -> a.id = id, a -> a.id, (a, data) -> a.data = data, a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING), (a, v) -> a.name = v, a -> a.name).add()
            .append(new KeyedCodec<>("Kind", Codec.STRING), (a, v) -> a.kind = v, a -> a.kind).add()
            .append(new KeyedCodec<>("Level", Codec.INTEGER), (a, v) -> a.level = v, a -> a.level).add()
            .append(new KeyedCodec<>("Cost", Codec.DOUBLE), (a, v) -> a.cost = v, a -> a.cost).add()
            .append(new KeyedCodec<>("Cooldown", Codec.DOUBLE), (a, v) -> a.cooldown = v, a -> a.cooldown).add()
            .append(new KeyedCodec<>("Effect", Codec.DOUBLE), (a, v) -> a.effect = v, a -> a.effect).add()
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (a, v) -> a.duration = v, a -> a.duration).add()
            .append(new KeyedCodec<>("EntityEffect", Codec.STRING), (a, v) -> a.entityEffect = v, a -> a.entityEffect).add()
            .append(new KeyedCodec<>("Element", Codec.STRING), (a, v) -> a.element = v, a -> a.element).add()
            .append(new KeyedCodec<>("OnHit", Codec.STRING), (a, v) -> a.onHit = v, a -> a.onHit).add()
            .append(new KeyedCodec<>("OnHitChance", Codec.DOUBLE), (a, v) -> a.onHitChance = v, a -> a.onHitChance).add()
            .append(new KeyedCodec<>("Pulse", Codec.DOUBLE), (a, v) -> a.pulse = v, a -> a.pulse).add()
            .append(new KeyedCodec<>("MaxTargets", Codec.INTEGER), (a, v) -> a.maxTargets = v, a -> a.maxTargets).add()
            .append(new KeyedCodec<>("Radius", Codec.DOUBLE), (a, v) -> a.radius = v, a -> a.radius).add()
            .build();

    private static AssetStore<String, Spell, DefaultAssetMap<String, Spell>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected String name;
    protected String kind;
    protected int level = 1;
    protected double cost = 1;
    protected double cooldown = 10;
    protected double effect = 0;
    protected double duration = 0;
    protected String entityEffect;
    protected String element;
    protected String onHit;
    protected double onHitChance = 1;
    protected double pulse = 3;
    protected int maxTargets = 1;
    protected double radius = 0;

    protected Spell() {
    }

    public static AssetStore<String, Spell, DefaultAssetMap<String, Spell>> getAssetStore() {
        if (assetStore == null) {
            assetStore = AssetRegistry.getAssetStore(Spell.class);
        }
        return assetStore;
    }

    public static DefaultAssetMap<String, Spell> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /**
     * @return the spell with this id, or null if there is none
     */
    @Nullable
    public static Spell get(@Nullable String id) {
        return id == null ? null : getAssetMap().getAsset(id);
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * @return the display name, or the id with spaces if the asset has none
     */
    public String getName() {
        return name == null ? id.replace('_', ' ') : name;
    }

    /**
     * @return the kind, or null if the asset's {@code Kind} isn't known
     */
    @Nullable
    public SpellKind getKind() {
        return SpellKind.of(kind);
    }

    public int getLevel() {
        return level;
    }

    public double getCost() {
        return cost;
    }

    public double getCooldown() {
        return cooldown;
    }

    public double getEffect() {
        return effect;
    }

    public double getDuration() {
        return duration;
    }

    @Nullable
    public String getEntityEffect() {
        return entityEffect;
    }

    /**
     * @return the status effect every hit gives while this spell's effect runs (a weapon
     * spell, e.g. {@code Burn}), or null
     */
    @Nullable
    public String getOnHit() {
        return onHit;
    }

    /**
     * @return a Weapon spell's element, which its {@code Effect} share of each hit is dealt as,
     * or null
     */
    @Nullable
    public String getElement() {
        return element;
    }

    /**
     * @return the elements in {@code Element}, split at commas (a Ward's), or an empty list
     */
    public List<String> getElements() {
        if (element == null || element.isBlank()) {
            return List.of();
        }
        return Arrays.stream(element.split(",")).map(String::trim).filter(e -> !e.isEmpty()).toList();
    }

    /** @return the chance per hit of giving {@link #getOnHit()}, 0 to 1 */
    public double getOnHitChance() {
        return onHitChance;
    }

    public double getPulse() {
        return pulse;
    }

    public int getMaxTargets() {
        return maxTargets;
    }

    public double getRadius() {
        return radius;
    }

    /**
     * @return this spell with a named piece's own numbers in place of its base ones, or this
     * spell itself if there are none. The copy keeps the id, so cooldowns are shared.
     */
    public Spell withOverrides(@Nullable SpellOverrides overrides) {
        if (overrides == null) {
            return this;
        }
        Spell copy = new Spell();
        copy.data = data;
        copy.id = id;
        copy.name = name;
        copy.kind = kind;
        copy.level = level;
        copy.cost = overrides.getCost() != null ? overrides.getCost() : cost;
        copy.cooldown = overrides.getCooldown() != null ? overrides.getCooldown() : cooldown;
        copy.effect = overrides.getEffect() != null ? overrides.getEffect() : effect;
        copy.duration = overrides.getDuration() != null ? overrides.getDuration() : duration;
        copy.entityEffect = entityEffect;
        copy.element = element;
        copy.onHit = onHit;
        copy.onHitChance = onHitChance;
        copy.pulse = pulse;
        copy.maxTargets = maxTargets;
        copy.radius = radius;
        return copy;
    }

    @Override
    public String toString() {
        return "Spell{" + id + "}";
    }
}
