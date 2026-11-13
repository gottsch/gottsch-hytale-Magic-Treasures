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

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.element.ElementBonusSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sees every hit after its damage is final (the inspect group, after vanilla applies it) and,
 * when a player dealt it, runs their hit spells ({@link SpellEngine#onHit}), such as Drain.
 * The spells run on the world thread after the systems, because a cast can rewrite the Jewelry
 * Case in the player's inventory.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class DrainDamageSystem extends DamageEventSystem {
    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return EntityStatMap.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage damage) {
        if (damage.isCancelled() || damage.getAmount() <= 0
                || !(damage.getSource() instanceof Damage.EntitySource source)
                // an element bonus hit is part of a hit already counted, and a status tick
                // (Burn) isn't a hit: neither starts hit spells (Nov 12)
                || ElementBonusSource.isBonus(damage)
                || damage.getSource() instanceof Damage.EntityEffectSource) {
            return;
        }
        Ref<EntityStore> attacker = source.getRef();
        if (attacker == null || !attacker.isValid() || attacker.getIndex() == chunk.getReferenceTo(index).getIndex()
                || commandBuffer.getComponent(attacker, Player.getComponentType()) == null) {
            return;
        }
        float amount = damage.getAmount();
        float[] reading = HitMeter.take(damage);
        PlayerRef attackerPlayer = commandBuffer.getComponent(attacker, PlayerRef.getComponentType());
        if (reading != null && attackerPlayer != null) {
            float base = reading[0];
            float bonus = reading[1] - reading[0];
            String percent = base > 0 ? String.format(" (+%.0f%%)", bonus / base * 100) : "";
            String crit = HitMeter.takeCrit(damage) ? ", CRIT x1.5" : "";
            attackerPlayer.sendMessage(Message.raw(String.format("Hit: base %.1f, bonus %.1f%s, after armor %.1f%s",
                    base, bonus, percent, amount, crit)).color("#ffd27a"));
        }
        Store<EntityStore> attackerStore = attacker.getStore();
        attackerStore.getExternalData().getWorld().execute(() -> {
            if (attacker.isValid()) {
                SpellEngine.onHit(attacker, attackerStore, amount);
            }
        });
    }
}
