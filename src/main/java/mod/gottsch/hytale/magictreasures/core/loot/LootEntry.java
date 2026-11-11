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

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One weighted choice in a {@link LootTable}:
 * <pre>
 * {"Items": ["MT_Scroll_.*"], "MinItemLevel": 1, "MaxItemLevel": 2, "Weight": 30}
 * {"Items": ["MT_(Ring|Necklace|Bracelet)_Gold_(Ruby|Topaz|Sapphire)_Great"],
 *  "Cuts": ["Flawless", "Perfect"], "Weight": 5}
 * </pre>
 * {@code Items} are regular expressions matched against whole item ids; one matching item is
 * picked at random. {@code MinItemLevel} / {@code MaxItemLevel} filter by the item's
 * {@code ItemLevel} (a spell scroll's is its spell level). {@code Count} is how many of it
 * (1 if absent). {@code Cuts} picks the gem cut of a gemmed piece of jewelry (Fine if absent).
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class LootEntry {
    public static final BuilderCodec<LootEntry> CODEC = BuilderCodec.builder(LootEntry.class, LootEntry::new)
            .append(new KeyedCodec<>("Items", Codec.STRING_ARRAY), (e, v) -> e.items = v, e -> e.items).add()
            .append(new KeyedCodec<>("Weight", Codec.INTEGER), (e, v) -> e.weight = v, e -> e.weight).add()
            .append(new KeyedCodec<>("MinCount", Codec.INTEGER), (e, v) -> e.minCount = v, e -> e.minCount).add()
            .append(new KeyedCodec<>("MaxCount", Codec.INTEGER), (e, v) -> e.maxCount = v, e -> e.maxCount).add()
            .append(new KeyedCodec<>("MinItemLevel", Codec.INTEGER), (e, v) -> e.minItemLevel = v, e -> e.minItemLevel).add()
            .append(new KeyedCodec<>("MaxItemLevel", Codec.INTEGER), (e, v) -> e.maxItemLevel = v, e -> e.maxItemLevel).add()
            .append(new KeyedCodec<>("Cuts", Codec.STRING_ARRAY), (e, v) -> e.cuts = v, e -> e.cuts).add()
            .build();

    public static final ArrayCodec<LootEntry> ARRAY_CODEC = new ArrayCodec<>(CODEC, LootEntry[]::new);
    public static final LootEntry[] NONE = new LootEntry[0];

    protected String[] items = new String[0];
    protected int weight = 1;
    protected int minCount = 1;
    protected int maxCount = 1;
    protected Integer minItemLevel;
    protected Integer maxItemLevel;
    protected String[] cuts;

    /** The matching item ids, worked out once (assets don't change while running). */
    private transient List<String> candidates;

    public LootEntry() {
    }

    public int getWeight() {
        return Math.max(0, weight);
    }

    public int getMinCount() {
        return Math.max(1, minCount);
    }

    public int getMaxCount() {
        return Math.max(getMinCount(), maxCount);
    }

    public String[] getCuts() {
        return cuts == null ? new String[0] : cuts;
    }

    /**
     * @return every item id this entry can give, sorted
     */
    public synchronized List<String> candidates() {
        if (candidates == null) {
            List<Pattern> patterns = new ArrayList<>();
            for (String item : items == null ? new String[0] : items) {
                patterns.add(Pattern.compile(item));
            }
            List<String> out = new ArrayList<>();
            for (Item item : Item.getAssetMap().getAssetMap().values()) {
                String id = item.getId();
                if (patterns.stream().noneMatch(p -> p.matcher(id).matches())) {
                    continue;
                }
                int level = item.getItemLevel();
                if ((minItemLevel != null && level < minItemLevel) || (maxItemLevel != null && level > maxItemLevel)) {
                    continue;
                }
                out.add(id);
            }
            out.sort(String::compareTo);
            candidates = List.copyOf(out);
        }
        return candidates;
    }
}
