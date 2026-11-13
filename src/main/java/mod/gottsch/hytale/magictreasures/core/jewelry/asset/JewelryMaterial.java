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
import mod.gottsch.hytale.magictreasures.core.element.ElementRoll;

import javax.annotation.Nullable;

/**
 * A jewelry material (gold, silver, bone, ...): a Magic Treasures JSON asset under
 * {@code Server/MagicTreasures/Materials/}. Its numbers are the base of a piece's stats;
 * the style multiplies them and the gem adds to them (see {@code JewelryStats}).
 * Asset packs can add their own materials.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class JewelryMaterial implements JsonAssetWithMap<String, DefaultAssetMap<String, JewelryMaterial>> {
    public static final AssetBuilderCodec<String, JewelryMaterial> CODEC = AssetBuilderCodec.builder(
                    JewelryMaterial.class, JewelryMaterial::new, Codec.STRING,
                    (a, id) -> a.id = id, a -> a.id, (a, data) -> a.data = data, a -> a.data)
            .append(new KeyedCodec<>("Mana", Codec.DOUBLE), (a, v) -> a.mana = v, a -> a.mana).add()
            .append(new KeyedCodec<>("Uses", Codec.INTEGER), (a, v) -> a.uses = v, a -> a.uses).add()
            .append(new KeyedCodec<>("Repairs", Codec.INTEGER), (a, v) -> a.repairs = v, a -> a.repairs).add()
            .append(new KeyedCodec<>("Recharges", Codec.INTEGER), (a, v) -> a.recharges = v, a -> a.recharges).add()
            .append(new KeyedCodec<>("RefillSeconds", Codec.DOUBLE), (a, v) -> a.refillSeconds = v, a -> a.refillSeconds).add()
            .append(new KeyedCodec<>("MaxLevel", Codec.INTEGER), (a, v) -> a.maxLevel = v, a -> a.maxLevel).add()
            .append(new KeyedCodec<>("Quality", Codec.STRING), (a, v) -> a.quality = v, a -> a.quality).add()
            .append(new KeyedCodec<>("Ingredient", Codec.STRING), (a, v) -> a.ingredient = v, a -> a.ingredient).add()
            .append(new KeyedCodec<>("GradientSet", Codec.STRING), (a, v) -> a.gradientSet = v, a -> a.gradientSet).add()
            .append(new KeyedCodec<>("Gradient", Codec.STRING), (a, v) -> a.gradient = v, a -> a.gradient).add()
            .append(new KeyedCodec<>("Factors", SpellFactors.CODEC), (a, v) -> a.factors = v, a -> a.factors).add()
            // elemental properties a piece of this metal may roll when made (Nov 11)
            .append(new KeyedCodec<>("Elements", ElementRoll.ARRAY_CODEC), (a, v) -> a.elements = v, a -> a.elements).add()
            .build();

    private static AssetStore<String, JewelryMaterial, DefaultAssetMap<String, JewelryMaterial>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected double mana = 0;
    protected int uses = 0;
    protected int repairs = 0;
    protected int recharges = 0;
    /** Seconds a worn piece takes to refill its mana from empty; 0 for the default (Nov 13). */
    protected double refillSeconds = 0;
    protected int maxLevel = 1;
    protected String quality = "Common";
    protected String ingredient;
    protected String gradientSet;
    protected String gradient;
    protected SpellFactors factors;
    protected ElementRoll[] elements;

    protected JewelryMaterial() {
    }

    /**
     * @return the elemental properties a piece of this material may roll
     */
    public ElementRoll[] getElements() {
        return elements == null ? ElementRoll.NONE : elements;
    }

    public static AssetStore<String, JewelryMaterial, DefaultAssetMap<String, JewelryMaterial>> getAssetStore() {
        if (assetStore == null) {
            assetStore = AssetRegistry.getAssetStore(JewelryMaterial.class);
        }
        return assetStore;
    }

    public static DefaultAssetMap<String, JewelryMaterial> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /**
     * @return the asset with this id, or null if there is none
     */
    @Nullable
    public static JewelryMaterial get(@Nullable String id) {
        return id == null ? null : getAssetMap().getAsset(id);
    }

    @Override
    public String getId() {
        return id;
    }

    public double getMana() {
        return mana;
    }

    public int getUses() {
        return uses;
    }

    public int getRepairs() {
        return repairs;
    }

    public int getRecharges() {
        return recharges;
    }

    /**
     * @return seconds a worn piece takes to refill its mana from empty, or 0 for the default
     * ({@code SpellEngine.REFILL_SECONDS})
     */
    public double getRefillSeconds() {
        return refillSeconds;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getQuality() {
        return quality;
    }

    @Nullable
    public String getIngredient() {
        return ingredient;
    }

    @Nullable
    public String getGradientSet() {
        return gradientSet;
    }

    @Nullable
    public String getGradient() {
        return gradient;
    }

    public SpellFactors getFactors() {
        return factors == null ? SpellFactors.NONE : factors;
    }


    @Override
    public String toString() {
        return "JewelryMaterial{" + id + "}";
    }
}
