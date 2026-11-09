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
 * A jewelry style (Regular, Great, Lord's): a Magic Treasures JSON asset under
 * {@code Server/MagicTreasures/Styles/}. It multiplies the material's mana and uses, adds
 * spell levels, repairs and quality steps, and sets how many rune sockets a piece has.
 * {@code Types} limits it to some jewelry types; when absent it fits every type.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class JewelryStyle implements JsonAssetWithMap<String, DefaultAssetMap<String, JewelryStyle>> {
    public static final AssetBuilderCodec<String, JewelryStyle> CODEC = AssetBuilderCodec.builder(
                    JewelryStyle.class, JewelryStyle::new, Codec.STRING,
                    (a, id) -> a.id = id, a -> a.id, (a, data) -> a.data = data, a -> a.data)
            .append(new KeyedCodec<>("Name", Codec.STRING), (a, v) -> a.name = v, a -> a.name).add()
            .append(new KeyedCodec<>("ManaMultiplier", Codec.DOUBLE), (a, v) -> a.manaMultiplier = v, a -> a.manaMultiplier).add()
            .append(new KeyedCodec<>("UsesMultiplier", Codec.DOUBLE), (a, v) -> a.usesMultiplier = v, a -> a.usesMultiplier).add()
            .append(new KeyedCodec<>("MaxLevel", Codec.INTEGER), (a, v) -> a.maxLevel = v, a -> a.maxLevel).add()
            .append(new KeyedCodec<>("Repairs", Codec.INTEGER), (a, v) -> a.repairs = v, a -> a.repairs).add()
            .append(new KeyedCodec<>("QualitySteps", Codec.INTEGER), (a, v) -> a.qualitySteps = v, a -> a.qualitySteps).add()
            .append(new KeyedCodec<>("Sockets", Codec.INTEGER), (a, v) -> a.sockets = v, a -> a.sockets).add()
            .append(new KeyedCodec<>("Types", Codec.STRING_ARRAY), (a, v) -> a.types = v, a -> a.types).add()
            .build();

    private static AssetStore<String, JewelryStyle, DefaultAssetMap<String, JewelryStyle>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected String name = "";
    protected double manaMultiplier = 1.0;
    protected double usesMultiplier = 1.0;
    protected int maxLevel = 0;
    protected int repairs = 0;
    protected int qualitySteps = 0;
    protected int sockets = 1;
    protected String[] types;

    protected JewelryStyle() {
    }

    public static AssetStore<String, JewelryStyle, DefaultAssetMap<String, JewelryStyle>> getAssetStore() {
        if (assetStore == null) {
            assetStore = AssetRegistry.getAssetStore(JewelryStyle.class);
        }
        return assetStore;
    }

    public static DefaultAssetMap<String, JewelryStyle> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /**
     * @return the asset with this id, or null if there is none
     */
    @Nullable
    public static JewelryStyle get(@Nullable String id) {
        return id == null ? null : getAssetMap().getAsset(id);
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getManaMultiplier() {
        return manaMultiplier;
    }

    public double getUsesMultiplier() {
        return usesMultiplier;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getRepairs() {
        return repairs;
    }

    public int getQualitySteps() {
        return qualitySteps;
    }

    public int getSockets() {
        return sockets;
    }

    @Nullable
    public String[] getTypes() {
        return types;
    }

    /**
     * @return true if this style can be used for the jewelry type (for example {@code Ring})
     */
    public boolean fits(String type) {
        if (types == null || types.length == 0) {
            return true;
        }
        for (String t : types) {
            if (t.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "JewelryStyle{" + id + "}";
    }
}
