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
package mod.gottsch.hytale.magictreasures.core.element;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.asset.type.particle.config.WorldParticle;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.spell.HitMeter;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import mod.gottsch.hytale.magictreasures.core.spell.SpellEngine;
import mod.gottsch.hytale.magictreasures.core.spell.SpellKind;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies worn jewelry's elemental properties to every hit (Nov 11), in the filter group after
 * vanilla adds effect bonuses and before armor:
 * <ul>
 *     <li>a player hit by elemental damage takes less (or, with a weakness, more) by their
 *     worn resistance to it, held within {@link ElementWear#RESISTANCE_CAP};</li>
 *     <li>a player's weapon hit may crit, by their worn crit chance: x1.5 damage, with
 *     vanilla's {@code Impact_Critical} particles;</li>
 *     <li>a player's weapon hit may give the target a status: each worn on-hit property tries
 *     its chance, and an active weapon spell (Flame Weapon) its {@code OnHitChance};</li>
 *     <li>a player's weapon hit adds element bonus hits (Nov 12): each worn element damage
 *     ("+10% as Fire damage") and active weapon spell (25% as Fire) deals that share of the
 *     hit, after crits, as a separate hit of its element ({@link ElementBonusSource}), so the
 *     target's resistances and weaknesses apply to it. They were multipliers on damage already
 *     of that element, and almost no weapon deals elemental damage.</li>
 * </ul>
 * A weapon hit is a player's direct hit, melee or ranged, of any damage type (the Flame
 * Longsword's swings are Fire): not a status tick ({@code EntityEffectSource}) and not a bonus
 * hit. Timed resistance buffs (the Ward spells) are vanilla entity effects, which vanilla applies
 * itself.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class ElementDamageSystem extends DamageEventSystem {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String CRIT_PARTICLES = "Impact_Critical";
    /** Above the target's feet: about chest height on a person-sized mob. */
    private static final double CRIT_HEIGHT = 1.0;
    /** The smallest element bonus hit dealt; less isn't worth a hit. */
    private static final float MIN_BONUS = 0.1f;

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
        DamageCause cause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        if (damage.isCancelled() || cause == null || damage.getAmount() <= 0) {
            return;
        }
        Ref<EntityStore> victim = chunk.getReferenceTo(index);
        float amount = damage.getAmount();
        // the hit before the victim's resistances: what bonus hits are a share of
        float dealt = amount;

        PlayerRef victimPlayer = commandBuffer.getComponent(victim, PlayerRef.getComponentType());
        if (victimPlayer != null && !cause.doesBypassResistances()) {
            amount *= (float) (1 - ElementWear.resistance(victimPlayer.getUuid(), cause));
        }
        if (victimPlayer != null) {
            // a Ward of this element starts now (Nov 12), after the damage systems
            store.getExternalData().getWorld().execute(() -> {
                if (victim.isValid()) {
                    SpellEngine.onHurt(victim, store, cause);
                }
            });
        }

        Ref<EntityStore> attacker = damage.getSource() instanceof Damage.EntitySource source ? source.getRef() : null;
        PlayerRef attackerPlayer = attacker == null || !attacker.isValid() || attacker.getIndex() == victim.getIndex()
                ? null : commandBuffer.getComponent(attacker, PlayerRef.getComponentType());
        boolean weaponHit = attackerPlayer != null && isWeaponHit(damage);
        if (weaponHit && (HitMeter.FORCE_CRITS.contains(attackerPlayer.getUuid())
                || ThreadLocalRandom.current().nextDouble() < ElementWear.critChance(attackerPlayer.getUuid()))) {
            amount *= (float) ElementWear.CRIT_MULTIPLIER;
            dealt *= (float) ElementWear.CRIT_MULTIPLIER;
            HitMeter.markCrit(damage);
            critParticles(damage);
            spawnCritParticles(victim, store);
        }
        damage.setAmount(Math.max(0, amount));

        if (weaponHit) {
            List<Spell> weaponSpells = activeWeaponSpells(attacker, commandBuffer);
            List<EntityEffect> statuses = onHit(attackerPlayer, weaponSpells);
            Map<String, Double> shares = bonusShares(attackerPlayer, weaponSpells);
            float base = dealt;
            if (!statuses.isEmpty() || !shares.isEmpty()) {
                // these change the target's components: do it after the systems
                store.getExternalData().getWorld().execute(() -> {
                    give(victim, statuses);
                    dealBonus(victim, attacker, shares, base);
                });
            }
        }
    }

    /**
     * @return true if this is a direct melee or ranged hit by an entity: not a status tick
     * (Burn, Poison) and not an element bonus hit
     */
    private static boolean isWeaponHit(Damage damage) {
        Damage.Source source = damage.getSource();
        return source instanceof Damage.EntitySource
                && !(source instanceof Damage.EntityEffectSource)
                && !(source instanceof ElementBonusSource);
    }

    /**
     * @return the weapon spells whose effect runs on the attacker now
     */
    private static List<Spell> activeWeaponSpells(Ref<EntityStore> attacker,
                                                  CommandBuffer<EntityStore> commandBuffer) {
        List<Spell> out = new ArrayList<>();
        EffectControllerComponent effects = commandBuffer.getComponent(attacker,
                EffectControllerComponent.getComponentType());
        if (effects == null) {
            return out;
        }
        for (Spell spell : Spell.getAssetMap().getAssetMap().values()) {
            if (spell.getKind() != SpellKind.WEAPON || spell.getEntityEffect() == null) {
                continue;
            }
            EntityEffect active = EntityEffect.getAssetMap().getAsset(spell.getEntityEffect());
            if (active != null && effects.hasEffect(active)) {
                out.add(spell);
            }
        }
        return out;
    }

    /**
     * @return the statuses this hit gives: worn on-hit properties that win their roll, and
     * every active weapon spell's that wins its {@code OnHitChance}
     */
    private static List<EntityEffect> onHit(PlayerRef player, List<Spell> weaponSpells) {
        List<EntityEffect> out = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ElementProperty property : ElementWear.properties(player.getUuid())) {
            if (property.kind() == ElementProperty.Kind.ON_HIT && property.effect() != null
                    && random.nextDouble() < property.value()) {
                add(out, property.effect());
            }
        }
        for (Spell spell : weaponSpells) {
            if (spell.getOnHit() != null && random.nextDouble() < spell.getOnHitChance()) {
                add(out, spell.getOnHit());
            }
        }
        return out;
    }

    /**
     * @return element id to the share of this hit to add as that element's damage: worn element
     * damage plus active weapon spells' {@code Effect}
     */
    private static Map<String, Double> bonusShares(PlayerRef player, List<Spell> weaponSpells) {
        Map<String, Double> out = ElementWear.damageShares(player.getUuid());
        for (Spell spell : weaponSpells) {
            if (spell.getElement() != null && spell.getEffect() > 0) {
                out.merge(spell.getElement(), spell.getEffect(), Double::sum);
            }
        }
        return out;
    }

    /**
     * Deals each element's share of the hit as its own hit, from the attacker, after the hit's
     * systems. A share too small to matter (under {@link #MIN_BONUS}) is skipped.
     */
    private static void dealBonus(Ref<EntityStore> victim, Ref<EntityStore> attacker,
                                  Map<String, Double> shares, float base) {
        if (shares.isEmpty() || !victim.isValid() || !attacker.isValid()) {
            return;
        }
        Store<EntityStore> store = victim.getStore();
        for (Map.Entry<String, Double> share : shares.entrySet()) {
            DamageCause cause = DamageCause.getAssetMap().getAsset(share.getKey());
            float extra = (float) (base * share.getValue());
            if (cause == null || extra < MIN_BONUS) {
                continue;
            }
            DamageSystems.executeDamage(victim, store,
                    new Damage(new ElementBonusSource(attacker), cause, extra));
        }
    }

    /**
     * Adds vanilla's critical-hit impact particles ({@code Impact_Critical}, as the dagger
     * pounce uses) to the hit's own.
     */
    private static void critParticles(Damage damage) {
        try {
            WorldParticle crit = new WorldParticle(CRIT_PARTICLES, null, 1f, null, null);
            Damage.Particles particles = damage.getIfPresentMetaObject(Damage.IMPACT_PARTICLES);
            if (particles == null) {
                damage.putMetaObject(Damage.IMPACT_PARTICLES,
                        new Damage.Particles(new ModelParticle[0], new WorldParticle[]{crit}, 75));
            } else {
                WorldParticle[] old = particles.getWorldParticles() == null ? new WorldParticle[0]
                        : particles.getWorldParticles();
                WorldParticle[] all = Arrays.copyOf(old, old.length + 1);
                all[old.length] = crit;
                particles.setWorldParticles(all);
            }
        } catch (RuntimeException e) {
            // the crit still counts; only its look is lost
            LOGGER.atFine().log("No crit particles: %s", e);
        }
    }

    /**
     * Spawns the crit particles at the target's chest as well. The hit's own impact particles
     * didn't show in game (Nov 11), so this doesn't rely on them. Done on the world thread,
     * after the damage systems.
     */
    private static void spawnCritParticles(Ref<EntityStore> victim, Store<EntityStore> store) {
        store.getExternalData().getWorld().execute(() -> {
            if (!victim.isValid()) {
                return;
            }
            TransformComponent transform = store.getComponent(victim, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            try {
                ParticleUtil.spawnParticleEffect(CRIT_PARTICLES,
                        new Vector3d(transform.getPosition()).add(0, CRIT_HEIGHT, 0), store);
            } catch (RuntimeException e) {
                LOGGER.atFine().log("No crit particles: %s", e);
            }
        });
    }

    private static void add(List<EntityEffect> out, String effectId) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (effect != null && !out.contains(effect)) {
            out.add(effect);
        }
    }

    private static void give(Ref<EntityStore> target, List<EntityEffect> statuses) {
        if (!target.isValid()) {
            return;
        }
        Store<EntityStore> store = target.getStore();
        EffectControllerComponent effects = store.getComponent(target, EffectControllerComponent.getComponentType());
        if (effects == null) {
            return;
        }
        for (EntityEffect effect : statuses) {
            effects.addEffect(target, effect, store);
        }
    }
}
