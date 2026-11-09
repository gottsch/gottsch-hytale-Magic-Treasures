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
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Measures a player's hits for {@code /mt hits}, so a damage buff such as Strength can be
 * checked. Vanilla adds effect bonuses to a hit in one system,
 * {@code DamageSystems.ScaleOutgoingDamageFromEntityEffects} (filter group), and armor is
 * taken off later. Two systems here read the hit's amount just before and just after that
 * system; {@link DrainDamageSystem} reads the final amount and prints all three:
 * the base, the bonus from effects, and the damage after the target's armor.
 * <p>
 * Only hits by players who turned the display on are measured.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class HitMeter {
    /** Players who asked to see their hits ({@code /mt hits}). */
    public static final Set<UUID> SHOW_HITS = ConcurrentHashMap.newKeySet();

    /** Per measured hit: {base, with bonus}. Weak, so dropped hits don't pile up. */
    private static final Map<Damage, float[]> READINGS = Collections.synchronizedMap(new WeakHashMap<>());

    private HitMeter() {
    }

    /**
     * @return {base, with bonus} for this hit, or null if it wasn't measured
     */
    @Nullable
    public static float[] take(Damage damage) {
        return READINGS.remove(damage);
    }

    /**
     * @return true if the hit was dealt by a player who turned the display on
     */
    static boolean measured(Damage damage, CommandBuffer<EntityStore> commandBuffer) {
        if (SHOW_HITS.isEmpty() || !(damage.getSource() instanceof Damage.EntitySource source)) {
            return false;
        }
        Ref<EntityStore> attacker = source.getRef();
        if (attacker == null || !attacker.isValid()) {
            return false;
        }
        PlayerRef player = commandBuffer.getComponent(attacker, PlayerRef.getComponentType());
        return player != null && SHOW_HITS.contains(player.getUuid());
    }

    /**
     * Reads the hit's amount before effect bonuses are added.
     */
    public static class Before extends DamageEventSystem {
        private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
                new SystemDependency<>(Order.BEFORE, DamageSystems.ScaleOutgoingDamageFromEntityEffects.class));

        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return DEPENDENCIES;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return EntityStatMap.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage damage) {
            if (measured(damage, commandBuffer)) {
                READINGS.put(damage, new float[]{damage.getAmount(), damage.getAmount()});
            }
        }
    }

    /**
     * Reads the hit's amount after effect bonuses are added, before armor.
     */
    public static class After extends DamageEventSystem {
        private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
                new SystemDependency<>(Order.AFTER, DamageSystems.ScaleOutgoingDamageFromEntityEffects.class),
                new SystemDependency<>(Order.BEFORE, DamageSystems.ArmorDamageReduction.class));

        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return DEPENDENCIES;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return EntityStatMap.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage damage) {
            float[] reading = READINGS.get(damage);
            if (reading != null) {
                reading[1] = damage.getAmount();
            }
        }
    }
}
