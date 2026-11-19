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
package mod.gottsch.hytale.magictreasures.core.spell;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.config.MagicTreasuresConfig;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refills a piece's charge at its metal's flat rate ({@code RefillPerMinute}), wherever the
 * piece is (v1.2, Mark Nov 18: take a piece off and let it refill, so a buff doesn't drain
 * it again the moment it's ready).
 * <p>
 * A piece keeps when its charge was last brought up to date ({@link JewelryState#getRefilledAt}),
 * and {@link #catchUp} adds what it earned since, so it doesn't need watching: a piece in a
 * chest catches up when it's next picked up or worn. Worn pieces catch up every second (the
 * panel shows their charge), pieces elsewhere in the inventory every {@link #INVENTORY_SECONDS}
 * so their tooltips stay near the truth. A piece is written only when a whole point is owed.
 * <p>
 * A full piece earns nothing: a cast that spends from full restarts its clock
 * ({@link #spent}), so time spent full isn't banked.
 *
 * @author Mark Gottschling on 11/18/2026
 */
public final class Refill {

    /** Charge a minute for a metal that doesn't say. */
    public static final double PER_MINUTE = 2;

    /** How often pieces not worn, elsewhere in the inventory, catch up. */
    public static final long INVENTORY_SECONDS = 30;

    /** Per player: when their inventory last caught up, epoch ms. */
    private static final Map<UUID, Long> SWEPT = new ConcurrentHashMap<>();

    private Refill() {
    }

    /**
     * @return charge the piece gets back each minute: its metal's rate times the config's
     * {@code RefillSpeed}
     */
    public static double perMinute(JewelryStats stats) {
        double rate = stats.material() == null ? 0 : stats.material().getRefillPerMinute();
        return (rate > 0 ? rate : PER_MINUTE) * MagicTreasuresConfig.get().getRefillSpeed();
    }

    /**
     * Brings the piece's charge up to date.
     *
     * @return the piece with its new state, or null if nothing changed
     */
    @Nullable
    public static ItemStack catchUp(ItemStack piece, long now) {
        JewelryStats stats = Jewelry.stats(piece);
        JewelryState state = Jewelry.state(piece);
        if (!stats.known() || state == null || stats.maxMana() <= 0
                || (stats.definition() != null && stats.definition().isUnlimited())) {
            return null;
        }
        // above its maximum, as a piece made before the charges changed (v1.2)
        if (state.getMana() > stats.maxMana()) {
            return Jewelry.withState(piece, state.copy().setMana(stats.maxMana()).setRefilledAt(now));
        }
        if (state.getMana() >= stats.maxMana()) {
            return null;
        }
        long at = state.getRefilledAt();
        double perMs = perMinute(stats) / 60_000;
        if (perMs <= 0) {
            // refilling is off (RefillSpeed 0)
            return null;
        }
        if (at <= 0 || at > now) {
            // never stamped (made before v1.2): start its clock now
            return Jewelry.withState(piece, state.copy().setRefilledAt(now));
        }
        double whole = Math.floor((now - at) * perMs);
        if (whole < 1) {
            return null;
        }
        double mana = Math.min(stats.maxMana(), state.getMana() + whole);
        // keep the part of a point not yet earned, unless it's full
        long stamp = mana >= stats.maxMana() ? now : at + Math.round(whole / perMs);
        return Jewelry.withState(piece, state.copy().setMana(mana).setRefilledAt(stamp));
    }

    /**
     * Restarts a piece's clock when a cast spends from full, so time spent full isn't banked.
     *
     * @param before its charge before the cast
     */
    static void spent(JewelryState state, JewelryStats stats, double before, long now) {
        if (before >= stats.maxMana()) {
            state.setRefilledAt(now);
        }
    }

    /**
     * Catches up the pieces in the player's inventory, not counting the Treasures Case's,
     * every {@link #INVENTORY_SECONDS}.
     */
    static void inventory(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, long now) {
        if (now - SWEPT.getOrDefault(uuid, 0L) < INVENTORY_SECONDS * 1000) {
            return;
        }
        SWEPT.put(uuid, now);
        for (ComponentType<EntityStore, ? extends InventoryComponent> type : List.of(
                InventoryComponent.Hotbar.getComponentType(), InventoryComponent.Storage.getComponentType(),
                InventoryComponent.Backpack.getComponentType(), InventoryComponent.Utility.getComponentType())) {
            InventoryComponent component = store.getComponent(ref, type);
            if (component == null) {
                continue;
            }
            ItemContainer container = component.getInventory();
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null || stack.isEmpty() || !Jewelry.isJewelry(stack)) {
                    continue;
                }
                ItemStack updated = catchUp(stack, now);
                if (updated != null) {
                    container.setItemStackForSlot(slot, updated);
                }
            }
        }
    }

    public static void onLeave(UUID uuid) {
        SWEPT.remove(uuid);
    }
}
