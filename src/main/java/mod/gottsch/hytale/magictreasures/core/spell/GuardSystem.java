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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.element.ElementBonusSource;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Guard spell (v1.3), on a hit the wearer blocks, with a shield or a weapon's guard:
 * <ul>
 *     <li>the block costs less stamina: the game spends stamina for a block in
 *     {@code DamageSystems.DamageStamina} (inspect group), times the hit's
 *     {@link Damage#STAMINA_DRAIN_MULTIPLIER}, which this lowers by the worn Guard's share. It
 *     needs no cast: it holds while a worn Guard piece has the charge for one.</li>
 *     <li>then the Guard is cast ({@link SpellEngine#onBlock}): a blocked melee hit stuns the
 *     attacker, once per cooldown, for its charge.</li>
 * </ul>
 * The game marks a blocked hit {@link Damage#BLOCKED} in {@code WieldingDamageReduction} (filter
 * group), for a hit from the front while guarding, so this runs after it.
 * <p>
 * The stamina share is kept per player ({@link #update}) whenever their worn jewelry is re-read,
 * as {@code ElementWear} keeps its properties, so a hit only reads a number.
 * <p>
 * With {@code /mt hits} on, each blocked hit prints the stamina it cost and the drain multiplier
 * before and after Guard, and {@link SpellEngine#onBlock} what the stun did.
 *
 * @author Mark Gottschling on 11/19/2026
 */
public class GuardSystem extends DamageEventSystem {

    /** The most a block's stamina is cut, however many Guards are worn. */
    public static final double MAX_CUT = 0.75;

    /** Per player: the share their worn Guard takes off a block's stamina. */
    private static final Map<UUID, Double> CUT = new ConcurrentHashMap<>();

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.AFTER, DamageSystems.WieldingDamageReduction.class));

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
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull Damage damage) {
        if (damage.isCancelled() || !Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED))) {
            return;
        }
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        double cut = CUT.getOrDefault(playerRef.getUuid(), 0.0);
        Float multiplier = damage.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER);
        float before = multiplier == null ? 1f : multiplier;
        float after = (float) (before * (1 - cut));
        if (cut > 0) {
            damage.putMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER, after);
        }
        Ref<EntityStore> victim = chunk.getReferenceTo(index);
        // /mt hits: the stamina before the game drains it (DamageStamina, inspect group)
        boolean show = HitMeter.SHOW_HITS.contains(playerRef.getUuid());
        float staminaBefore = show ? stamina(chunk.getComponent(index, EntityStatMap.getComponentType())) : 0;
        float hit = damage.getAmount();
        // the stun: a direct hit by an entity, not a status tick or a bonus hit
        Ref<EntityStore> attacker = damage.getSource() instanceof Damage.EntitySource source
                && !(source instanceof Damage.EntityEffectSource) && !(source instanceof ElementBonusSource)
                ? source.getRef() : null;
        boolean stun = attacker != null && attacker.isValid() && attacker.getIndex() != victim.getIndex();
        if (!show && !stun) {
            return;
        }
        // it changes the inventory and the attacker's effects: do it after the systems
        store.getExternalData().getWorld().execute(() -> {
            if (!victim.isValid()) {
                return;
            }
            if (show) {
                float staminaAfter = stamina(store.getComponent(victim, EntityStatMap.getComponentType()));
                String guard = cut > 0 ? String.format(" (Guard -%.0f%%)", cut * 100) : " (no Guard)";
                playerRef.sendMessage(Message.raw(String.format(
                        "Blocked: hit %.1f after block, stamina %.2f -> %.2f (spent %.2f), drain x%.2f -> x%.2f%s",
                        hit, staminaBefore, staminaAfter, staminaBefore - staminaAfter, before, after, guard))
                        .color("#9fe0b0"));
            }
            if (stun) {
                SpellEngine.onBlock(victim, store, attacker);
            }
        });
    }

    private static float stamina(@Nullable EntityStatMap stats) {
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getStamina());
        return value == null ? 0 : value.get();
    }

    /**
     * Re-reads the share the player's worn Guard takes off a block's stamina: the best of the
     * worn pieces with a Guard spell and the charge to cast it, within {@link #MAX_CUT}.
     */
    public static void update(UUID uuid, WornJewelry worn) {
        double best = 0;
        for (ItemStack piece : worn.pieces()) {
            best = Math.max(best, cut(piece));
        }
        if (best > 0) {
            CUT.put(uuid, Math.min(MAX_CUT, best));
        } else {
            CUT.remove(uuid);
        }
    }

    /**
     * @return the share of a block's stamina this piece's Guard takes off, 0 if it has none or
     * not the charge for it
     */
    private static double cut(ItemStack piece) {
        JewelryStats stats = Jewelry.stats(piece);
        JewelryState state = Jewelry.state(piece);
        if (!stats.known() || state == null) {
            return 0;
        }
        JewelryDefinition definition = stats.definition();
        double best = 0;
        for (String spellId : state.getSpells()) {
            Spell spell = Spell.get(spellId);
            if (spell == null || spell.getKind() != SpellKind.GUARD) {
                continue;
            }
            spell = spell.withOverrides(definition.getSpellOverrides());
            double cost = definition.isUnlimited() ? 0 : spell.getCost() * stats.factors().getCost();
            if (state.getMana() >= cost) {
                best = Math.max(best, spell.getEffect() * stats.factors().getEffect());
            }
        }
        return best;
    }

    /**
     * @return the share the player's worn Guard takes off a block's stamina now
     */
    public static double cut(UUID uuid) {
        return CUT.getOrDefault(uuid, 0.0);
    }

    public static void onLeave(UUID uuid) {
        CUT.remove(uuid);
    }
}
