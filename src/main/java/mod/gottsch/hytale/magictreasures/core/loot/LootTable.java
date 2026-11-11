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
package mod.gottsch.hytale.magictreasures.core.loot;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A Magic Treasures loot table: a JSON asset under {@code Server/MagicTreasures/Loot/}.
 * Our loot is added in code, not through vanilla drop lists, so another mod replacing a
 * vanilla drop list can't remove it (decided with Mark, Nov 11). Each table says which
 * vanilla drop lists it rides along with:
 * <ul>
 *     <li>{@code Source}: {@code Chest} (a chest filled from its drop list as it appears in
 *     the world) or {@code Mob} (a mob's death drop list).</li>
 *     <li>{@code Match}: regular expressions matched against the whole drop-list id, for
 *     example {@code .*_Tier3} or {@code Zone3_.*}.</li>
 *     <li>{@code Priority}: when several tables match, the highest wins.</li>
 *     <li>{@code Chance}: how likely the chest or mob gets anything from us.</li>
 *     <li>{@code MinRolls} / {@code MaxRolls}: how many entries are picked when it does.</li>
 *     <li>{@code Entries}: the weighted {@link LootEntry} choices.</li>
 * </ul>
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class LootTable implements JsonAssetWithMap<String, DefaultAssetMap<String, LootTable>> {
    public static final AssetBuilderCodec<String, LootTable> CODEC = AssetBuilderCodec.builder(
                    LootTable.class, LootTable::new, Codec.STRING,
                    (a, id) -> a.id = id, a -> a.id, (a, data) -> a.data = data, a -> a.data)
            .append(new KeyedCodec<>("Source", Codec.STRING), (a, v) -> a.source = v, a -> a.source).add()
            .append(new KeyedCodec<>("Match", Codec.STRING_ARRAY), (a, v) -> a.match = v, a -> a.match).add()
            .append(new KeyedCodec<>("Priority", Codec.INTEGER), (a, v) -> a.priority = v, a -> a.priority).add()
            .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (a, v) -> a.chance = v, a -> a.chance).add()
            .append(new KeyedCodec<>("MinRolls", Codec.INTEGER), (a, v) -> a.minRolls = v, a -> a.minRolls).add()
            .append(new KeyedCodec<>("MaxRolls", Codec.INTEGER), (a, v) -> a.maxRolls = v, a -> a.maxRolls).add()
            .append(new KeyedCodec<>("Entries", LootEntry.ARRAY_CODEC), (a, v) -> a.entries = v, a -> a.entries).add()
            .build();

    public static final String CHEST = "Chest";
    public static final String MOB = "Mob";

    private static AssetStore<String, LootTable, DefaultAssetMap<String, LootTable>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected String source = CHEST;
    protected String[] match;
    protected int priority = 0;
    protected double chance = 1.0;
    protected int minRolls = 1;
    protected int maxRolls = 1;
    protected LootEntry[] entries;

    private transient List<Pattern> patterns;

    protected LootTable() {
    }

    public static AssetStore<String, LootTable, DefaultAssetMap<String, LootTable>> getAssetStore() {
        if (assetStore == null) {
            assetStore = AssetRegistry.getAssetStore(LootTable.class);
        }
        return assetStore;
    }

    public static DefaultAssetMap<String, LootTable> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /**
     * @return the highest-priority table of this source whose {@code Match} covers the drop
     * list, or null if none does
     */
    @Nullable
    public static LootTable forDropList(String source, @Nullable String dropList) {
        if (dropList == null || dropList.isEmpty()) {
            return null;
        }
        LootTable best = null;
        for (LootTable table : getAssetMap().getAssetMap().values()) {
            if (table.source.equalsIgnoreCase(source) && table.matches(dropList)
                    && (best == null || table.priority > best.priority)) {
                best = table;
            }
        }
        return best;
    }

    @Override
    public String getId() {
        return id;
    }

    public double getChance() {
        return chance;
    }

    public int getMinRolls() {
        return Math.max(0, minRolls);
    }

    public int getMaxRolls() {
        return Math.max(getMinRolls(), maxRolls);
    }

    public LootEntry[] getEntries() {
        return entries == null ? LootEntry.NONE : entries;
    }

    public synchronized boolean matches(String dropList) {
        if (patterns == null) {
            List<Pattern> out = new ArrayList<>();
            for (String m : match == null ? new String[0] : match) {
                out.add(Pattern.compile(m, Pattern.CASE_INSENSITIVE));
            }
            patterns = out;
        }
        return patterns.stream().anyMatch(p -> p.matcher(dropList).matches());
    }

    @Override
    public String toString() {
        return "LootTable{" + id + "}";
    }
}
