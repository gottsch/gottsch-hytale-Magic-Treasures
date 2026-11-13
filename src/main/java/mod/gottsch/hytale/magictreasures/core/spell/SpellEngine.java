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
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.element.ElementBonusSource;
import mod.gottsch.hytale.magictreasures.core.element.ElementWear;
import mod.gottsch.hytale.magictreasures.core.hud.JewelryHud;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the passive spells on worn jewelry.
 * <p>
 * {@link #tick} runs once a second for each player ({@link SpellTickSystem}) and tries every
 * {@link SpellKind.Trigger#TICK} spell; {@link #onHit} runs when the player damages something
 * ({@link HitSpellDamageSystem}) and tries every {@link SpellKind.Trigger#HIT} spell. Both run on
 * the player's world thread, outside the ECS systems, so they can change the inventory.
 * <p>
 * {@link #onHurt} runs when the player takes damage and tries every
 * {@link SpellKind.Trigger#HURT} spell (the Wards) whose element it is.
 * <p>
 * A spell casts when the piece's cooldown for it is over and the piece has the mana. A cast
 * that does something (a heal, a buff started) spends the mana and one use, and starts the
 * cooldown, all scaled by the piece's factors. A piece with no uses left breaks, as in 2.0.
 * Mana is the piece's own well (decision 3). A worn piece refills it, empty to full in
 * its material's {@code RefillSeconds} (default {@link #REFILL_SECONDS}; Nov 12: with no
 * refill a plain copper piece had 3 casts in it and its 75 uses never mattered); Recharges fill
 * it at once.
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

    /**
     * Seconds a worn piece takes to refill its mana from empty to full, when its material
     * doesn't say ({@code RefillSeconds}, Nov 13). Was the same for every material (Nov 12).
     */
    public static final double REFILL_SECONDS = 600;

    /**
     * Per player: the share of each hit that the last cast of each Weapon spell adds as its
     * element, keyed by spell id: its {@code Effect} (or the named piece's) times the piece's
     * effect factor (Nov 13: the 25% ignored the piece's factors, which scaled only the
     * duration).
     */
    private static final Map<UUID, Map<String, Double>> WEAPON_SHARES = new ConcurrentHashMap<>();

    /**
     * Per player: refilled mana not yet written to each worn piece, keyed {@code slot:itemId}.
     * A piece is written only when a whole point is owed, so the inventory doesn't change every
     * second.
     */
    private static final Map<UUID, Map<String, Double>> REFILL = new ConcurrentHashMap<>();

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
        DrainAura.tick(uuid, ref, store);
        WornJewelry worn = WornJewelry.of(ref, store);
        cast(SpellKind.Trigger.TICK, uuid, ref, store, worn, 0, null, null);
        if (worn.pieces().isEmpty()) {
            REFILL.remove(uuid);
            JewelryHud.refresh(playerRef, ref, store, worn);
            return;
        }
        // re-read: a cast may have spent mana or broken a piece
        refill(uuid, WornJewelry.of(ref, store));
        JewelryHud.refresh(playerRef, ref, store, WornJewelry.of(ref, store));
    }

    /**
     * Tries every hit spell on the attacker's worn jewelry.
     *
     * @param damage the damage the hit dealt
     * @param victim what was hit, for the Strikes
     */
    public static void onHit(Ref<EntityStore> ref, Store<EntityStore> store, float damage,
                             Ref<EntityStore> victim) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || damage <= 0) {
            return;
        }
        WornJewelry worn = WornJewelry.of(ref, store);
        cast(SpellKind.Trigger.HIT, playerRef.getUuid(), ref, store, worn, damage, null, victim);
    }

    /**
     * Tries every hurt spell (the Wards) on the player's worn jewelry that wards this damage.
     */
    public static void onHurt(Ref<EntityStore> ref, Store<EntityStore> store, DamageCause cause) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        WornJewelry worn = WornJewelry.of(ref, store);
        if (!worn.pieces().isEmpty()) {
            cast(SpellKind.Trigger.HURT, playerRef.getUuid(), ref, store, worn, 0, cause, null);
        }
    }

    /**
     * Refills the mana of each worn piece that isn't full: its max mana over its refill time
     * ({@link #refillSeconds}), once a second.
     */
    private static void refill(UUID uuid, WornJewelry worn) {
        Map<String, Double> owed = REFILL.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Set<String> kept = new HashSet<>();
        for (int i = 0; i < worn.pieces().size(); i++) {
            ItemStack piece = worn.pieces().get(i);
            short slot = worn.pieceSlots().get(i);
            JewelryStats stats = Jewelry.stats(piece);
            JewelryState state = Jewelry.state(piece);
            if (!stats.known() || state == null || stats.maxMana() <= 0 || state.getMana() >= stats.maxMana()) {
                continue;
            }
            String key = slot + ":" + piece.getItemId();
            kept.add(key);
            double total = owed.getOrDefault(key, 0.0)
                    + stats.maxMana() / refillSeconds(stats) * SpellTickSystem.INTERVAL_SECONDS;
            double whole = Math.floor(total);
            owed.put(key, total - whole);
            if (whole > 0) {
                double mana = Math.min(stats.maxMana(), state.getMana() + whole);
                worn.replace(slot, Jewelry.withState(piece, state.copy().setMana(mana)));
            }
        }
        owed.keySet().retainAll(kept);
    }

    /**
     * @return seconds the piece takes to refill from empty: its material's
     * {@code RefillSeconds}, or {@link #REFILL_SECONDS}
     */
    private static double refillSeconds(JewelryStats stats) {
        double seconds = stats.material() == null ? 0 : stats.material().getRefillSeconds();
        return seconds > 0 ? seconds : REFILL_SECONDS;
    }

    /**
     * Spends one use of the piece in this case slot, after it boosted a rune cast. A piece
     * with no uses left breaks.
     *
     * @param pieceId the piece's item id when the cast started, so a piece swapped in since
     *                isn't charged
     */
    public static void useRune(Ref<EntityStore> ref, Store<EntityStore> store, short slot, String pieceId) {
        WornJewelry worn = WornJewelry.of(ref, store);
        int i = worn.pieceSlots().indexOf(slot);
        if (i < 0 || !worn.pieces().get(i).getItemId().equals(pieceId)) {
            return;
        }
        ItemStack piece = worn.pieces().get(i);
        JewelryState state = Jewelry.state(piece);
        if (state == null || Jewelry.stats(piece).definition().isUnbreakable()) {
            return;
        }
        state = state.copy().setUses(state.getUses() - 1);
        if (state.getUses() > 0) {
            worn.replace(slot, Jewelry.withState(piece, state));
            return;
        }
        worn.replace(slot, null);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerRef.sendMessage(Message.raw("Your " + Jewelry.displayName(piece) + " is worn out and broke.")
                    .color("#ff7070"));
            Map<String, Long> cooldowns = COOLDOWNS.get(playerRef.getUuid());
            if (cooldowns != null) {
                clearCooldowns(cooldowns, slot);
            }
        }
    }

    /**
     * @return the share of each hit this Weapon spell adds as its element for the player, as
     * the piece that last cast it set it, or the spell's own {@code Effect} if none is known
     */
    public static double weaponShare(UUID uuid, Spell spell) {
        Map<String, Double> shares = WEAPON_SHARES.get(uuid);
        return shares == null ? spell.getEffect() : shares.getOrDefault(spell.getId(), spell.getEffect());
    }

    public static void onLeave(UUID uuid) {
        DrainAura.onLeave(uuid);
        WEAPON_SHARES.remove(uuid);
        COOLDOWNS.remove(uuid);
        LIT.remove(uuid);
        REFILL.remove(uuid);
    }

    /**
     * @param cause for a {@link SpellKind.Trigger#HURT} cast, the damage taken: only spells
     *              warding it are tried
     * @param victim for a {@link SpellKind.Trigger#HIT} cast, what was hit
     */
    private static void cast(SpellKind.Trigger trigger, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store,
                             WornJewelry worn, float damage, @Nullable DamageCause cause,
                             @Nullable Ref<EntityStore> victim) {
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
            JewelryDefinition definition = stats.definition();
            boolean changed = false;
            for (String spellId : state.getSpells()) {
                Spell spell = Spell.get(spellId);
                if (spell == null || spell.getKind() == null || spell.getKind().getTrigger() != trigger) {
                    continue;
                }
                // a named piece's own numbers (Nov 13)
                spell = spell.withOverrides(definition.getSpellOverrides());
                if (trigger == SpellKind.Trigger.HURT && !wards(spell, cause)) {
                    continue;
                }
                String key = slot + ":" + spellId;
                if (cooldowns.getOrDefault(key, 0L) > now) {
                    continue;
                }
                double cost = definition.isUnlimited() ? 0 : spell.getCost() * factors.getCost();
                if (state.getMana() < cost) {
                    continue;
                }
                if (!run(spell, factors, uuid, ref, store, damage, victim)) {
                    continue;
                }
                state.setMana(Math.max(0, state.getMana() - cost));
                if (!definition.isUnbreakable()) {
                    state.setUses(state.getUses() - 1);
                }
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

    /**
     * @return true if one of the spell's elements is this damage (or a parent of it)
     */
    private static boolean wards(Spell spell, @Nullable DamageCause cause) {
        for (String element : spell.getElements()) {
            if (ElementWear.matches(element, cause)) {
                return true;
            }
        }
        return false;
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
                               Store<EntityStore> store, float damage, @Nullable Ref<EntityStore> victim) {
        return switch (spell.getKind()) {
            case HEALING -> heal(ref, store, spell.getEffect() * factors.getEffect());
            // vampiric heals by part of the hit: at most the spell's amount, at most the damage dealt
            case VAMPIRIC -> heal(ref, store, Math.min(spell.getEffect() * factors.getEffect(), damage));
            case STRIKE -> strike(spell, factors, ref, victim);
            case DRAIN -> DrainAura.start(uuid, spell, factors, ref, store);
            case WEAPON -> {
                if (buff(spell, factors, ref, store) == null) {
                    yield false;
                }
                WEAPON_SHARES.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .put(spell.getId(), spell.getEffect() * factors.getEffect());
                yield true;
            }
            case SPEED, STRENGTH, WARD -> buff(spell, factors, ref, store) != null;
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
     * A Strike: deals the spell's flat {@code Effect} (scaled by the piece) to what was hit, as
     * its own hit of the spell's {@code Element}. It's an {@link ElementBonusSource} hit, like
     * worn element damage, so it doesn't crit, give statuses or start hit spells, and
     * {@code /mt hits} shows it (Nov 13).
     * <p>
     * It lands after the hit that started it, so that hit may have killed the target: vanilla
     * then cancels it ({@code DamageSystems.FilterUnkillable} cancels damage to the dying, the
     * invulnerable and the intangible). A Strike on a target with no health left isn't tried,
     * and a cancelled one isn't charged, so it fires on the next hit instead (Nov 13: a Storm
     * Strike went on cooldown with no hit to show).
     *
     * @return true if it dealt the hit
     */
    private static boolean strike(Spell spell, SpellFactors factors, Ref<EntityStore> attacker,
                                  @Nullable Ref<EntityStore> victim) {
        if (victim == null || !victim.isValid() || spell.getElement() == null) {
            return false;
        }
        Store<EntityStore> victimStore = victim.getStore();
        EntityStatMap stats = victimStore.getComponent(victim, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= health.getMin()) {
            return false;
        }
        DamageCause cause = DamageCause.getAssetMap().getAsset(spell.getElement());
        float amount = (float) (spell.getEffect() * factors.getEffect());
        if (cause == null || amount <= 0) {
            return false;
        }
        Damage hit = new Damage(new ElementBonusSource(attacker), cause, amount);
        DamageSystems.executeDamage(victim, victimStore, hit);
        if (hit.isCancelled()) {
            PlayerRef player = attacker.getStore().getComponent(attacker, PlayerRef.getComponentType());
            if (player != null && HitMeter.SHOW_HITS.contains(player.getUuid())) {
                player.sendMessage(Message.raw("  " + spell.getName() + " cancelled by the target (not spent)")
                        .color("#9fd8ff"));
            }
            return false;
        }
        return true;
    }

    /**
     * Heals the player, if hurt, by up to {@code amount}.
     *
     * @return true if any health was restored
     */
    static boolean heal(Ref<EntityStore> ref, Store<EntityStore> store, double amount) {
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
