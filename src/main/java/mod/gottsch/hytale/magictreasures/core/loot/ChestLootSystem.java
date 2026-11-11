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

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds our loot to world chests (Nov 11). Vanilla's stash system
 * ({@code StashPlugin$StashSystem}) fills a chest from its drop list when the chest's block is
 * added to the world, then clears the drop-list name. This system runs just before it, reads
 * the same name, and puts the matching {@link LootTable}'s roll into empty slots. It never
 * touches the vanilla drop lists, so a mod replacing them can't remove our loot.
 * <p>
 * The stash class isn't public, so it is ordered against by name; if it can't be found
 * (Hytale renamed it), the system logs a warning and runs unordered.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class ChestLootSystem extends RefSystem<ChunkStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String STASH_SYSTEM = "com.hypixel.hytale.builtin.adventure.stash.StashPlugin$StashSystem";

    private final Query<ChunkStore> query = Query.and(ItemContainerBlock.getComponentType(),
            BlockModule.BlockStateInfo.getComponentType());
    private final Set<Dependency<ChunkStore>> dependencies;

    @SuppressWarnings("unchecked")
    public ChestLootSystem() {
        Set<Dependency<ChunkStore>> deps = Set.of();
        try {
            Class<? extends ISystem<ChunkStore>> stash =
                    (Class<? extends ISystem<ChunkStore>>) Class.forName(STASH_SYSTEM);
            deps = Set.of(new SystemDependency<>(Order.BEFORE, stash));
        } catch (ClassNotFoundException | ClassCastException e) {
            LOGGER.atWarning().log("Can't find %s; chest loot runs unordered and may miss chests", STASH_SYSTEM);
        }
        this.dependencies = deps;
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store,
                              @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // as the stash does: not in worlds that keep block spawners unresolved (prefab editing)
        if (!store.getExternalData().getWorld().getWorldConfig().isBlockSpawnersResolved()) {
            return;
        }
        ItemContainerBlock chest = store.getComponent(ref, ItemContainerBlock.getComponentType());
        BlockModule.BlockStateInfo info = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (chest == null || info == null || chest.getDroplist() == null) {
            return;
        }
        LootTable table = LootTable.forDropList(LootTable.CHEST, chest.getDroplist());
        if (table == null) {
            return;
        }
        List<ItemStack> loot = LootRoller.roll(table, ThreadLocalRandom.current());
        if (loot.isEmpty()) {
            return;
        }
        SimpleItemContainer container = chest.getItemContainer();
        if (container == null) {
            return;
        }
        // into empty slots, scattered, leaving the rest for the vanilla loot
        List<Short> empty = new ArrayList<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack there = container.getItemStack(slot);
            if (there == null || there.isEmpty()) {
                empty.add(slot);
            }
        }
        java.util.Collections.shuffle(empty, ThreadLocalRandom.current());
        int placed = 0;
        for (int i = 0; i < loot.size() && i < empty.size(); i++) {
            if (container.setItemStackForSlot(empty.get(i), loot.get(i)).succeeded()) {
                placed++;
            }
        }
        if (placed > 0) {
            info.markNeedsSaving();
            LOGGER.atFine().log("Chest %s (%s): added %d items", chest.getDroplist(), table.getId(), placed);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
    }
}
