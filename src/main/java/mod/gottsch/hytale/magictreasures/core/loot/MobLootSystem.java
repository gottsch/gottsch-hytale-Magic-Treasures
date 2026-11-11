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
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drops our loot when a mob dies (Nov 11): the mob's role names its vanilla drop list
 * ({@code Drop_Skeleton_...}), and the matching {@link LootTable} with source {@code Mob}
 * rolls what we add. The items are dropped at the body the same way vanilla drops death
 * items ({@code ItemComponent.generateItemDrops}), so they don't depend on the vanilla drop
 * list's contents.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class MobLootSystem extends DeathSystems.OnDeathSystem {
    private final Query<EntityStore> query = Query.and(NPCEntity.getComponentType(),
            TransformComponent.getComponentType());

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent death,
                                 @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (npc == null || transform == null) {
            return;
        }
        Role role = npc.getRole();
        LootTable table = LootTable.forDropList(LootTable.MOB, role == null ? null : role.getDropListId());
        if (table == null) {
            return;
        }
        List<ItemStack> loot = LootRoller.roll(table, ThreadLocalRandom.current());
        if (loot.isEmpty()) {
            return;
        }
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f rotation = head == null ? new Rotation3f() : new Rotation3f(head.getRotation());
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(commandBuffer, loot,
                transform.getPosition(), rotation);
        commandBuffer.addEntities(drops, AddReason.SPAWN);
    }
}
