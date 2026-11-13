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

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Drain auras (Lesser Drain, Drain, Greater Drain): once cast, every {@code Pulse} seconds
 * for {@code Duration} seconds, the aura takes {@code Effect} health from each of up to
 * {@code MaxTargets} hostile mobs within {@code Radius} blocks, nearest first, without contact,
 * and heals the wearer by the total taken.
 * <ul>
 *     <li>It is cast (and costs mana) only when a hostile mob is in range, so it doesn't
 *     drain the piece out of a fight.</li>
 *     <li>Damage is its job and healing the bonus: every pulse damages, even at full health,
 *     and heals as much of the total as the wearer is missing (Mark, Nov 13: a heal-only
 *     aura would overlap the Healing and Vampiric lines and sit idle in a fight going
 *     well).</li>
 *     <li>Hostile means an NPC whose default attitude to players is {@link Attitude#HOSTILE}:
 *     livestock, pets and villagers are never drained, and neither is a neutral mob you
 *     angered.</li>
 *     <li>The damage is {@code MT_Drain}, which bypasses resistances, from a
 *     {@link DrainSource}: the player gets kill credit, but it isn't a weapon hit.</li>
 *     <li>The piece's factors scale the amount (effect), the length (duration) and the
 *     radius (range).</li>
 *     <li>Each pulse shows vanilla particles: {@code VoidImpact} on every drained mob and
 *     {@code Elite_Heal} on the player when it heals.</li>
 * </ul>
 * Runs on the world thread, from {@link SpellEngine#tick} once a second, so a pulse lands on
 * the first second after it is due.
 *
 * @author Mark Gottschling on 11/13/2026
 */
public final class DrainAura {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** The damage cause asset ({@code Server/Entity/Damage/MT_Drain.json}). */
    public static final String CAUSE = "MT_Drain";
    /** Vanilla particles on each drained mob: a dark purple splash with smoke. */
    private static final String TARGET_PARTICLES = "VoidImpact";
    /** Vanilla particles on the player when a pulse heals: red rays rising for 2 s. */
    private static final String HEAL_PARTICLES = "Elite_Heal";
    /** Above a mob's feet, about its chest, for {@link #TARGET_PARTICLES}. */
    private static final double TARGET_HEIGHT = 1.0;

    /** An aura running on a player. */
    private static final class Active {
        final String spellId;
        final double effect;
        final int maxTargets;
        final double radius;
        final long pulseMillis;
        final long endsAt;
        long nextPulseAt;

        Active(String spellId, double effect, int maxTargets, double radius, long pulseMillis,
               long endsAt, long nextPulseAt) {
            this.spellId = spellId;
            this.effect = effect;
            this.maxTargets = maxTargets;
            this.radius = radius;
            this.pulseMillis = pulseMillis;
            this.endsAt = endsAt;
            this.nextPulseAt = nextPulseAt;
        }
    }

    /** Player to the auras running on them. */
    private static final Map<UUID, List<Active>> ACTIVE = new ConcurrentHashMap<>();

    private DrainAura() {
    }

    /**
     * Starts the aura, if a hostile mob is in range and the same aura isn't already running.
     * The first pulse is at once.
     *
     * @return true if it started, so it costs mana and a use
     */
    static boolean start(UUID uuid, Spell spell, SpellFactors factors, Ref<EntityStore> ref,
                         Store<EntityStore> store) {
        double radius = spell.getRadius() * factors.getRange();
        double duration = spell.getDuration() * factors.getDuration();
        if (radius <= 0 || duration <= 0 || spell.getMaxTargets() <= 0 || spell.getPulse() <= 0) {
            return false;
        }
        List<Active> running = ACTIVE.computeIfAbsent(uuid, k -> new ArrayList<>());
        for (Active active : running) {
            if (active.spellId.equals(spell.getId())) {
                return false;
            }
        }
        if (targets(ref, store, radius, 1).isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Active active = new Active(spell.getId(), spell.getEffect() * factors.getEffect(),
                spell.getMaxTargets(), radius, Math.round(spell.getPulse() * 1000),
                now + Math.round(duration * 1000), now);
        running.add(active);
        pulse(active, ref, store);
        active.nextPulseAt = now + active.pulseMillis;
        return true;
    }

    /**
     * Pulses each running aura that is due, and ends the ones that ran out.
     */
    static void tick(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        List<Active> running = ACTIVE.get(uuid);
        if (running == null || running.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Active> it = running.iterator();
        while (it.hasNext()) {
            Active active = it.next();
            if (now >= active.nextPulseAt && active.nextPulseAt < active.endsAt) {
                pulse(active, ref, store);
                active.nextPulseAt += active.pulseMillis;
            }
            if (now >= active.endsAt) {
                it.remove();
            }
        }
    }

    static void onLeave(UUID uuid) {
        ACTIVE.remove(uuid);
    }

    /**
     * Takes the aura's amount from each target, at most the health it has, and heals the
     * player by the total, as far as they are hurt. The heal particles show only when it heals.
     */
    private static void pulse(Active active, Ref<EntityStore> ref, Store<EntityStore> store) {
        DamageCause cause = DamageCause.getAssetMap().getAsset(CAUSE);
        if (cause == null) {
            LOGGER.atWarning().log("Damage cause %s is missing; the Drain aura does nothing", CAUSE);
            return;
        }
        double total = 0;
        for (Ref<EntityStore> target : targets(ref, store, active.radius, active.maxTargets)) {
            float amount = (float) Math.min(active.effect, health(target, store));
            if (amount <= 0) {
                continue;
            }
            // before the damage, which may kill the mob and remove it
            particles(TARGET_PARTICLES, target, TARGET_HEIGHT, store);
            DamageSystems.executeDamage(target, store, new Damage(new DrainSource(ref), cause, amount));
            total += amount;
        }
        if (total > 0 && SpellEngine.heal(ref, store, total)) {
            particles(HEAL_PARTICLES, ref, 0, store);
        }
    }

    /**
     * Shows a vanilla particle system at the entity, {@code height} blocks above its feet, to
     * the players nearby. Missing particles only cost the look, never the pulse.
     */
    private static void particles(String system, Ref<EntityStore> entity, double height, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(entity, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }
        try {
            ParticleUtil.spawnParticleEffect(system, new Vector3d(transform.getPosition()).add(0, height, 0), store);
        } catch (RuntimeException e) {
            LOGGER.atFine().log("No %s particles: %s", system, e);
        }
    }

    /**
     * @return up to {@code max} living hostile mobs within {@code radius} blocks of the
     * player, nearest first
     */
    private static List<Ref<EntityStore>> targets(Ref<EntityStore> ref, Store<EntityStore> store,
                                                  double radius, int max) {
        List<Ref<EntityStore>> out = new ArrayList<>();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return out;
        }
        SpatialResource<Ref<EntityStore>, EntityStore> npcs =
                store.getResource(NPCPlugin.get().getNpcSpatialResource());
        List<Ref<EntityStore>> near = new ArrayList<>();
        npcs.getSpatialStructure().ordered(transform.getPosition(), radius, near);
        for (Ref<EntityStore> npc : near) {
            if (out.size() >= max) {
                break;
            }
            if (npc.isValid() && !npc.equals(ref) && health(npc, store) > 0 && hostile(npc, store)) {
                out.add(npc);
            }
        }
        return out;
    }

    private static boolean hostile(Ref<EntityStore> npc, Store<EntityStore> store) {
        WorldSupport support = store.getComponent(npc, WorldSupport.getComponentType());
        return support != null && support.getDefaultPlayerAttitude() == Attitude.HOSTILE;
    }

    /**
     * @return the entity's health above its minimum (0 when dead or without health)
     */
    private static double health(Ref<EntityStore> entity, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(entity, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value == null ? 0 : Math.max(0, value.get() - value.getMin());
    }
}
