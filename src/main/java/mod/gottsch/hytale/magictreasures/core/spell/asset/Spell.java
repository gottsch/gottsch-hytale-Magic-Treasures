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
import mod.gottsch.hytale.magictreasures.core.spell.SpellKind;

import javax.annotation.Nullable;

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
 *     <li>{@code Effect}: health healed or drained.</li>
 *     <li>{@code Duration}: seconds a buff lasts.</li>
 *     <li>{@code EntityEffect}: the effect asset a buff applies (speed, strength, and the
 *     status icon).</li>
 * </ul>
 * The wearing piece's factors scale cost, cooldown, effect and duration when it casts.
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

    @Override
    public String toString() {
        return "Spell{" + id + "}";
    }
}
