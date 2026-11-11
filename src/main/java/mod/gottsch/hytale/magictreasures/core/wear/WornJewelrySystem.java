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
package mod.gottsch.hytale.magictreasures.core.wear;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import mod.gottsch.hytale.magictreasures.core.element.ElementWear;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Re-reads a player's worn jewelry whenever their inventory changes: the Jewelry Case
 * moved, added or removed, or a piece put into or taken out of the case (the case's
 * contents are saved back to its stack, which changes the inventory).
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class WornJewelrySystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    public WornJewelrySystem() {
        super(InventoryChangeEvent.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InventoryChangeEvent event) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        JewelryWear.update(ref, commandBuffer, false);
        // jewelry that was crafted or made before elements gets its roll (Nov 11); writing the
        // inventory raises this event again, with nothing left to stamp
        if (ElementWear.needsStamp(ref, commandBuffer)) {
            store.getExternalData().getWorld().execute(() -> {
                if (ref.isValid()) {
                    ElementWear.stamp(ref, ref.getStore());
                }
            });
        }
    }
}
