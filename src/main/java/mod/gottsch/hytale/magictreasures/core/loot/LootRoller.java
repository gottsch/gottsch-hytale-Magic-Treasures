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

import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rolls a {@link LootTable} into item stacks.
 * <p>
 * The loot package has no Magic Treasures dependencies, so it can move into a shared library
 * (a GottschCore for Hytale, Mark Nov 11) later. A mod whose items need more than
 * {@code new ItemStack(id, count)} (Magic Treasures' jewelry rolls its elements and takes a
 * gem cut) sets an {@link ItemFactory}.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public final class LootRoller {

    /**
     * Makes the stack for a rolled item.
     */
    @FunctionalInterface
    public interface ItemFactory {
        /**
         * @param itemId the rolled item
         * @param count  how many
         * @param cut    a cut picked from the entry's {@code Cuts}, or null
         * @return the stack, or null to drop the roll
         */
        @Nullable
        ItemStack create(String itemId, int count, @Nullable String cut);
    }

    private static volatile ItemFactory factory = (id, count, cut) -> new ItemStack(id, count);

    private LootRoller() {
    }

    public static void setItemFactory(ItemFactory itemFactory) {
        factory = itemFactory;
    }

    /**
     * Rolls the table: its chance first, then its rolls.
     *
     * @return the stacks, empty if the chance failed or nothing could be made
     */
    public static List<ItemStack> roll(LootTable table, Random random) {
        if (random.nextDouble() >= table.getChance()) {
            return List.of();
        }
        return rollEntries(table, random);
    }

    /**
     * Rolls the table's entries, skipping its chance (for testing).
     */
    public static List<ItemStack> rollEntries(LootTable table, Random random) {
        List<ItemStack> out = new ArrayList<>();
        int rolls = table.getMinRolls() + random.nextInt(table.getMaxRolls() - table.getMinRolls() + 1);
        for (int i = 0; i < rolls; i++) {
            LootEntry entry = pick(table.getEntries(), random);
            if (entry == null) {
                continue;
            }
            List<String> candidates = entry.candidates();
            if (candidates.isEmpty()) {
                continue;
            }
            String itemId = candidates.get(random.nextInt(candidates.size()));
            int count = entry.getMinCount() + random.nextInt(entry.getMaxCount() - entry.getMinCount() + 1);
            String[] cuts = entry.getCuts();
            String cut = cuts.length == 0 ? null : cuts[random.nextInt(cuts.length)];
            ItemStack stack = factory.create(itemId, count, cut);
            if (stack != null && !stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    @Nullable
    private static LootEntry pick(LootEntry[] entries, Random random) {
        int total = 0;
        for (LootEntry entry : entries) {
            if (!entry.candidates().isEmpty()) {
                total += entry.getWeight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int at = random.nextInt(total);
        for (LootEntry entry : entries) {
            if (entry.candidates().isEmpty()) {
                continue;
            }
            at -= entry.getWeight();
            if (at < 0) {
                return entry;
            }
        }
        return null;
    }
}
