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
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.hud.JewelryHud;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the passive spells on worn jewelry.
 * <p>
 * {@link #tick} runs once a second for each player ({@link SpellTickSystem}) and tries every
 * {@link SpellKind.Trigger#TICK} spell; {@link #onHit} runs when the player damages something
 * ({@link DrainDamageSystem}) and tries every {@link SpellKind.Trigger#HIT} spell. Both run on
 * the player's world thread, outside the ECS systems, so they can change the inventory.
 * <p>
 * A spell casts when the piece's cooldown for it is over and the piece has the mana. A cast
 * that does something (a heal, a buff started) spends the mana and one use, and starts the
 * cooldown, all scaled by the piece's factors. A piece with no uses left breaks, as in 2.0.
 * Mana is the piece's own well (decision 3) and doesn't refill over time.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class SpellEngine {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cat Sight's light: a soft white, reach about 12 blocks, small radius to keep the tint. */
    private static final ColorLight CAT_SIGHT_LIGHT = new ColorLight((byte) 1, (byte) 12, (byte) 12, (byte) 13);

    /** Per player: when each piece's spell is ready again, keyed {@code slot:spellId}, in epoch ms. */
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    /** Players lit by Cat Sight, and the effect that keeps the light on. */
    private static final Map<UUID, String> LIT = new ConcurrentHashMap<>();

    private SpellEngine() {
    }

    /**
     * Tries every tick spell on the player's worn jewelry, then refreshes their HUD.
     */
    public static void tick(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        endCatSight(uuid, ref, store);
        WornJewelry worn = WornJewelry.of(ref, store);
        cast(SpellKind.Trigger.TICK, uuid, ref, store, worn, 0);
        // re-read: a cast may have spent mana or broken a piece
        JewelryHud.refresh(playerRef, ref, store, worn.pieces().isEmpty() ? worn : WornJewelry.of(ref, store));
    }

    /**
     * Tries every hit spell on the attacker's worn jewelry.
     *
     * @param damage the damage the hit dealt
     */
    public static void onHit(Ref<EntityStore> ref, Store<EntityStore> store, float damage) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || damage <= 0) {
            return;
        }
        WornJewelry worn = WornJewelry.of(ref, store);
        cast(SpellKind.Trigger.HIT, playerRef.getUuid(), ref, store, worn, damage);
    }

    public static void onLeave(UUID uuid) {
        COOLDOWNS.remove(uuid);
        LIT.remove(uuid);
    }

    private static void cast(SpellKind.Trigger trigger, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store,
                             WornJewelry worn, float damage) {
        long now = System.currentTimeMillis();
        Map<String, Long> cooldowns = COOLDOWNS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        for (int i = 0; i < worn.pieces().size(); i++) {
            ItemStack piece = worn.pieces().get(i);
            short slot = worn.pieceSlots().get(i);
            JewelryStats stats = Jewelry.stats(piece);
            JewelryState state = Jewelry.state(piece);
            if (!stats.known() || state == null || state.getSpells().length == 0) {
                continue;
            }
            state = state.copy();
            SpellFactors factors = stats.factors();
            boolean changed = false;
            for (String spellId : state.getSpells()) {
                Spell spell = Spell.get(spellId);
                if (spell == null || spell.getKind() == null || spell.getKind().getTrigger() != trigger) {
                    continue;
                }
                String key = slot + ":" + spellId;
                if (cooldowns.getOrDefault(key, 0L) > now) {
                    continue;
                }
                double cost = spell.getCost() * factors.getCost();
                if (state.getMana() < cost) {
                    continue;
                }
                if (!run(spell, factors, uuid, ref, store, damage)) {
                    continue;
                }
                state.setMana(Math.max(0, state.getMana() - cost));
                state.setUses(state.getUses() - 1);
                cooldowns.put(key, now + Math.round(spell.getCooldown() * factors.getCooldown() * 1000));
                changed = true;
                LOGGER.atFine().log("%s cast %s from %s: mana %.1f, uses %d", uuid, spellId, piece.getItemId(),
                        state.getMana(), state.getUses());
                if (state.getUses() <= 0) {
                    break;
                }
            }
            if (!changed) {
                continue;
            }
            if (state.getUses() <= 0) {
                worn.replace(slot, null);
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    playerRef.sendMessage(Message.raw("Your " + Jewelry.displayName(piece) + " is worn out and broke.")
                            .color("#ff7070"));
                }
                clearCooldowns(cooldowns, slot);
            } else {
                worn.replace(slot, Jewelry.withState(piece, state));
            }
        }
    }

    private static void clearCooldowns(Map<String, Long> cooldowns, short slot) {
        cooldowns.keySet().removeIf(key -> key.startsWith(slot + ":"));
    }

    /**
     * Runs one spell.
     *
     * @return true if it did something, so it costs mana and a use
     */
    private static boolean run(Spell spell, SpellFactors factors, UUID uuid, Ref<EntityStore> ref,
                               Store<EntityStore> store, float damage) {
        return switch (spell.getKind()) {
            case HEALING -> heal(ref, store, spell.getEffect() * factors.getEffect());
            // drain heals by part of the hit: at most the spell's amount, at most the damage dealt
            case DRAIN -> heal(ref, store, Math.min(spell.getEffect() * factors.getEffect(), damage));
            case SPEED, STRENGTH -> buff(spell, factors, ref, store) != null;
            case CAT_SIGHT -> {
                EntityEffect effect = buff(spell, factors, ref, store);
                if (effect == null) {
                    yield false;
                }
                store.putComponent(ref, DynamicLight.getComponentType(), new DynamicLight(new ColorLight(CAT_SIGHT_LIGHT)));
                LIT.put(uuid, effect.getId());
                yield true;
            }
        };
    }

    /**
     * Heals the player, if hurt, by up to {@code amount}.
     *
     * @return true if any health was restored
     */
    private static boolean heal(Ref<EntityStore> ref, Store<EntityStore> store, double amount) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null || amount <= 0) {
            return false;
        }
        int health = DefaultEntityStatTypes.getHealth();
        EntityStatValue value = stats.get(health);
        if (value == null || value.get() <= value.getMin() || value.get() >= value.getMax()) {
            return false;
        }
        stats.addStatValue(health, (float) Math.min(amount, value.getMax() - value.get()));
        return true;
    }

    /**
     * Starts the spell's effect for its duration, unless it is already running.
     *
     * @return the effect started, or null if none was
     */
    @Nullable
    private static EntityEffect buff(Spell spell, SpellFactors factors, Ref<EntityStore> ref, Store<EntityStore> store) {
        EntityEffect effect = spell.getEntityEffect() == null ? null : EntityEffect.getAssetMap().getAsset(spell.getEntityEffect());
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effect == null || effects == null || effects.hasEffect(effect)) {
            return null;
        }
        float duration = (float) (spell.getDuration() * factors.getDuration());
        return effects.addEffect(ref, effect, duration, OverlapBehavior.OVERWRITE, store) ? effect : null;
    }

    /**
     * Turns Cat Sight's light off once its effect has ended (it ran out, or the player died).
     */
    private static void endCatSight(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        String effectId = LIT.get(uuid);
        if (effectId == null) {
            return;
        }
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (effects == null || effect == null || !effects.hasEffect(effect)) {
            store.removeComponentIfExists(ref, DynamicLight.getComponentType());
            LIT.remove(uuid);
        }
    }

    /**
     * @return seconds until this piece's spell is ready, 0 if it is ready now
     */
    public static double cooldownLeft(UUID uuid, short slot, String spellId) {
        Map<String, Long> cooldowns = COOLDOWNS.getOrDefault(uuid, new HashMap<>());
        long left = cooldowns.getOrDefault(slot + ":" + spellId, 0L) - System.currentTimeMillis();
        return Math.max(0, left / 1000.0);
    }
}
