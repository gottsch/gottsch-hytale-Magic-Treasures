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

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.plugin.registry.AssetRegistry;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;

/**
 * Registers Magic Treasures' own JSON asset types. Each loads from a folder under
 * {@code Server/} in every asset pack, the plugin's own included, so other packs can add
 * materials, styles, gems, jewelry and spells.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class JewelryAssets {
    public static final String MATERIALS_PATH = "MagicTreasures/Materials";
    public static final String STYLES_PATH = "MagicTreasures/Styles";
    public static final String GEMS_PATH = "MagicTreasures/Gems";
    public static final String JEWELRY_PATH = "MagicTreasures/Jewelry";
    public static final String SPELLS_PATH = "MagicTreasures/Spells";

    private JewelryAssets() {
    }

    public static void register(AssetRegistry registry) {
        registry.register(HytaleAssetStore.builder(JewelryMaterial.class, new DefaultAssetMap<>())
                .setPath(MATERIALS_PATH)
                .setCodec(JewelryMaterial.CODEC)
                .setKeyFunction(JewelryMaterial::getId)
                .build());
        registry.register(HytaleAssetStore.builder(JewelryStyle.class, new DefaultAssetMap<>())
                .setPath(STYLES_PATH)
                .setCodec(JewelryStyle.CODEC)
                .setKeyFunction(JewelryStyle::getId)
                .build());
        registry.register(HytaleAssetStore.builder(Gemstone.class, new DefaultAssetMap<>())
                .setPath(GEMS_PATH)
                .setCodec(Gemstone.CODEC)
                .setKeyFunction(Gemstone::getId)
                .build());
        registry.register(HytaleAssetStore.builder(JewelryDefinition.class, new DefaultAssetMap<>())
                .setPath(JEWELRY_PATH)
                .setCodec(JewelryDefinition.CODEC)
                .setKeyFunction(JewelryDefinition::getId)
                .build());
        registry.register(HytaleAssetStore.builder(Spell.class, new DefaultAssetMap<>())
                .setPath(SPELLS_PATH)
                .setCodec(Spell.CODEC)
                .setKeyFunction(Spell::getId)
                .build());
    }
}
