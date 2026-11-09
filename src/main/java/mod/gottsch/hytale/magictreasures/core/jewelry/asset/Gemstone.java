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

package mod.gottsch.hytale.magictreasures.core.jewelry.asset;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nullable;

/**
 * A gem that can be set in jewelry: a Magic Treasures JSON asset under
 * {@code Server/MagicTreasures/Gems/}. {@code Item} is the gem's item (a vanilla
 * {@code Rock_Gem_*} for now). A gem adds mana and recharges, and its factors multiply
 * the material's. This folds Minecraft 2.0's gem and stone tier into one asset.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class Gemstone implements JsonAssetWithMap<String, DefaultAssetMap<String, Gemstone>> {
    public static final AssetBuilderCodec<String, Gemstone> CODEC = AssetBuilderCodec.builder(
                    Gemstone.class, Gemstone::new, Codec.STRING,
                    (a, id) -> a.id = id, a -> a.id, (a, data) -> a.data = data, a -> a.data)
            .append(new KeyedCodec<>("Item", Codec.STRING), (a, v) -> a.item = v, a -> a.item).add()
            .append(new KeyedCodec<>("Mana", Codec.DOUBLE), (a, v) -> a.mana = v, a -> a.mana).add()
            .append(new KeyedCodec<>("Recharges", Codec.INTEGER), (a, v) -> a.recharges = v, a -> a.recharges).add()
            .append(new KeyedCodec<>("Quality", Codec.STRING), (a, v) -> a.quality = v, a -> a.quality).add()
            .append(new KeyedCodec<>("Factors", SpellFactors.CODEC), (a, v) -> a.factors = v, a -> a.factors).add()
            .build();

    private static AssetStore<String, Gemstone, DefaultAssetMap<String, Gemstone>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected String item;
    protected double mana = 0;
    protected int recharges = 0;
    protected String quality = "Common";
    protected SpellFactors factors;

    protected Gemstone() {
    }

    public static AssetStore<String, Gemstone, DefaultAssetMap<String, Gemstone>> getAssetStore() {
        if (assetStore == null) {
            assetStore = AssetRegistry.getAssetStore(Gemstone.class);
        }
        return assetStore;
    }

    public static DefaultAssetMap<String, Gemstone> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /**
     * @return the asset with this id, or null if there is none
     */
    @Nullable
    public static Gemstone get(@Nullable String id) {
        return id == null ? null : getAssetMap().getAsset(id);
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public String getItem() {
        return item;
    }

    public double getMana() {
        return mana;
    }

    public int getRecharges() {
        return recharges;
    }

    public String getQuality() {
        return quality;
    }

    public SpellFactors getFactors() {
        return factors == null ? SpellFactors.NONE : factors;
    }


    @Override
    public String toString() {
        return "Gemstone{" + id + "}";
    }
}
