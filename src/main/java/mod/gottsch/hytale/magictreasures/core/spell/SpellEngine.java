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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3d;
import mod.gottsch.hytale.magictreasures.core.config.MagicTreasuresConfig;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mod.gottsch.hytale.magictreasures.core.lang.Lang;

/**
 * Runs the passive spells on worn jewelry.
 * <p>
 * {@link #tick} runs once a second for each player ({@link SpellTickSystem}) and tries every
 * {@link SpellKind.Trigger#TICK} spell; {@link #onHit} runs when the player damages something
 * ({@link HitSpellDamageSystem}) and tries every {@link SpellKind.Trigger#HIT} spell. Both run on
 * the player's world thread, outside the ECS systems, so they can change the inventory.
 * <p>
 * {@link #onHurt} runs when the player takes damage and tries every
 * {@link SpellKind.Trigger#HURT} spell (Wards, Thorns, Barrier) whose element it is.
 * <p>
 * A spell casts when the piece's cooldown for it is over and the piece has the mana. A cast
 * that does something (a heal, a buff started) spends the mana and one use, and starts the
 * cooldown, all scaled by the piece's factors. A piece with no uses left breaks, as in 2.0.
 * Mana is the piece's own well (decision 3). A piece refills it at its metal's flat rate,
 * worn or not ({@link Refill}, v1.2; Nov 12: with no refill a plain copper piece had 3 casts
 * in it and its 75 uses never mattered); Recharges fill it at once.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class SpellEngine {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Cat Sight's light: a soft white, reach about 12 blocks, small radius to keep the tint. */
    private static final ColorLight CAT_SIGHT_LIGHT = new ColorLight((byte) 1, (byte) 12, (byte) 12, (byte) 13);
    /**
     * Cat Sight casts only where the light is at most this, of 15 (v1.2: it kept casting in
     * daylight). Dusk passes it; a torch-lit room doesn't.
     */
    public static final int DARK_LIGHT = 7;

    /** Per player: when each piece's spell is ready again, keyed {@code slot:spellId}, in epoch ms. */
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    /** Players lit by Cat Sight, and the effect that keeps the light on. */
    private static final Map<UUID, String> LIT = new ConcurrentHashMap<>();

    /** A Barrier starts when a hit leaves the wearer below this share of their health. */
    public static final double BARRIER_HEALTH = 0.35;

    /** The damage cause Thorns hands back. */
    private static final String THORNS_CAUSE = "Physical";

    /**
     * Stamina each cast of a piece under the Curse of Weariness takes (v1.2). The bar holds 10
     * and refills 3 a second at rest, so it bites in a fight; it never takes the bar below 0,
     * so the curse weakens but never leaves the wearer overdrawn (Mark, Nov 18).
     */
    public static final double WEARINESS_STAMINA = 2;

    /**
     * Per player: the share of each hit that the last cast of each Weapon spell adds as its
     * element, keyed by spell id: its {@code Effect} (or the named piece's) times the piece's
     * effect factor (Nov 13: the 25% ignored the piece's factors, which scaled only the
     * duration).
     */
    private static final Map<UUID, Map<String, Double>> WEAPON_SHARES = new ConcurrentHashMap<>();

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
        long now = System.currentTimeMillis();
        Refill.inventory(uuid, ref, store, now);
        if (worn.pieces().isEmpty()) {
            JewelryHud.refresh(playerRef, ref, store, worn);
            return;
        }
        // re-read: a cast may have spent mana or broken a piece
        refill(WornJewelry.of(ref, store), now);
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
     * Tries every hurt spell (Wards, Thorns, Barrier) on the player's worn jewelry whose
     * elements match this damage.
     *
     * @param damage   the hit taken, after worn resistances and before armor
     * @param attacker who dealt it, for Thorns: only for a direct hit by an entity, else null
     */
    public static void onHurt(Ref<EntityStore> ref, Store<EntityStore> store, DamageCause cause,
                              float damage, @Nullable Ref<EntityStore> attacker) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        WornJewelry worn = WornJewelry.of(ref, store);
        if (!worn.pieces().isEmpty()) {
            cast(SpellKind.Trigger.HURT, playerRef.getUuid(), ref, store, worn, damage, cause, attacker);
        }
    }

    /**
     * Brings each worn piece's charge up to date ({@link Refill#catchUp}), once a second.
     */
    private static void refill(WornJewelry worn, long now) {
        for (int i = 0; i < worn.pieces().size(); i++) {
            ItemStack updated = Refill.catchUp(worn.pieces().get(i), now);
            if (updated != null) {
                worn.replace(worn.pieceSlots().get(i), updated);
            }
        }
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
            playerRef.sendMessage(Lang.of("spell.broke").param("piece", Jewelry.name(piece))
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
        Refill.onLeave(uuid);
    }

    /**
     * @param damage for a {@link SpellKind.Trigger#HIT} cast the damage dealt, for a
     *               {@link SpellKind.Trigger#HURT} cast the damage taken
     * @param cause for a {@link SpellKind.Trigger#HURT} cast, the damage taken: only spells
     *              warding it are tried
     * @param victim for a {@link SpellKind.Trigger#HIT} cast, what was hit; for a
     *               {@link SpellKind.Trigger#HURT} cast, the attacker, if any
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
                Refill.spent(state, stats, state.getMana(), now);
                state.setMana(Math.max(0, state.getMana() - cost));
                if (definition.hasCurse(JewelryDefinition.CURSE_WEARINESS)) {
                    weary(ref, store);
                }
                if (!definition.isUnbreakable()) {
                    state.setUses(state.getUses() - 1);
                }
                cooldowns.put(key, now + Math.round(spell.getCooldown() * factors.getCooldown()
                        * MagicTreasuresConfig.get().getSpellCooldowns() * 1000));
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
                    playerRef.sendMessage(Lang.of("spell.broke").param("piece", Jewelry.name(piece))
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
            case VAMPIRIC -> vampiric(spell, factors, ref, store, victim);
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
            case THORNS -> thorns(spell, factors, ref, damage, victim);
            case BARRIER -> barrier(spell, factors, ref, store);
            case CAT_SIGHT -> {
                // only in the dark: at night or underground, not in daylight (v1.2)
                if (!dark(ref, store)) {
                    yield false;
                }
                EntityEffect effect = buff(spell, factors, ref, store);
                if (effect == null) {
                    yield false;
                }
                // why it cast, while it's tested (Nov 18: it kept casting in daylight)
                LOGGER.atInfo().log("Cat Sight cast for %s. %s", uuid, describeLight(ref, store));
                store.putComponent(ref, DynamicLight.getComponentType(), new DynamicLight(new ColorLight(CAT_SIGHT_LIGHT)));
                LIT.put(uuid, effect.getId());
                yield true;
            }
        };
    }

    /**
     * A Vampiric spell: drains the spell's {@code Effect} (scaled by the piece) from what was
     * hit, as its own hit, and heals the wearer by what it drained. The drain is an absolute
     * amount, whatever the weapon dealt, held only to the health the target has left after
     * the hit (Mark, Nov 13: capped at the damage dealt, Bite and Embrace healed no more than
     * Kiss with an ordinary weapon; uncapped, the health came from nowhere).
     * <ul>
     *     <li>It drains only while the wearer is hurt, so it keeps its cooldown for when it
     *     heals.</li>
     *     <li>The drain is {@code MT_Drain} damage from a {@link DrainSource}, as the Drain aura
     *     deals: it bypasses resistances, the wearer gets kill credit, and it doesn't crit or
     *     start hit spells.</li>
     *     <li>A target the hit killed, or one vanilla won't damage, has nothing to give: the
     *     spell isn't charged and tries again on the next hit, as a Strike does.</li>
     * </ul>
     *
     * @return true if it drained and healed
     */
    private static boolean vampiric(Spell spell, SpellFactors factors, Ref<EntityStore> ref, Store<EntityStore> store,
                                    @Nullable Ref<EntityStore> victim) {
        if (victim == null || !victim.isValid() || !hurt(ref, store)) {
            return false;
        }
        Store<EntityStore> victimStore = victim.getStore();
        EntityStatMap stats = victimStore.getComponent(victim, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= health.getMin()) {
            return false;
        }
        DamageCause cause = DamageCause.getAssetMap().getAsset(DrainAura.CAUSE);
        float drained = (float) Math.min(spell.getEffect() * factors.getEffect(), health.get() - health.getMin());
        if (cause == null || drained <= 0) {
            return false;
        }
        Damage hit = new Damage(new DrainSource(ref), cause, drained);
        DamageSystems.executeDamage(victim, victimStore, hit);
        if (hit.isCancelled()) {
            return false;
        }
        heal(ref, store, drained);
        return true;
    }

    /**
     * @return true if the player is alive and below full health
     */
    private static boolean hurt(Ref<EntityStore> ref, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value != null && value.get() > value.getMin() && value.get() < value.getMax();
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
     * Thorns: deals the spell's {@code Effect} share (scaled by the piece) of the hit the wearer
     * took back to the attacker, as the wearer's own {@code Physical} hit. It's an
     * {@link ElementBonusSource} hit, like a Strike: the attacker's resistances and armor apply,
     * the wearer gets the kill, and it doesn't crit or start hit spells. That also keeps two
     * wearers from bouncing it back and forth, as only direct hits start Thorns. A hit vanilla
     * cancels isn't charged.
     *
     * @return true if it dealt the hit
     */
    private static boolean thorns(Spell spell, SpellFactors factors, Ref<EntityStore> wearer, float taken,
                                  @Nullable Ref<EntityStore> attacker) {
        if (attacker == null || !attacker.isValid() || taken <= 0) {
            return false;
        }
        Store<EntityStore> attackerStore = attacker.getStore();
        EntityStatMap stats = attackerStore.getComponent(attacker, EntityStatMap.getComponentType());
        EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= health.getMin()) {
            return false;
        }
        DamageCause cause = DamageCause.getAssetMap().getAsset(THORNS_CAUSE);
        float amount = (float) (taken * spell.getEffect() * factors.getEffect());
        if (cause == null || amount < 0.1f) {
            return false;
        }
        Damage hit = new Damage(new ElementBonusSource(wearer), cause, amount);
        DamageSystems.executeDamage(attacker, attackerStore, hit);
        return !hit.isCancelled();
    }

    /**
     * @return true if the light where the player's head is is at most {@link #DARK_LIGHT}: the
     * brighter of the block light and the sky light scaled by the time of day, as the game's
     * spawning reads it (LightRangePredicate.calculateLightValue, 0 to 15). Sky light is 15 in
     * the open by day and falls toward 0 at night; underground it is 0, so torches decide.
     * The player's own dynamic lights (Cat Sight, a held torch) aren't in the chunk's light.
     */
    static boolean dark(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) {
            return false;
        }
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.x());
        int z = (int) Math.floor(position.z());
        // the brighter of the feet's and head's blocks: a solid block reads 0, and on a slope
        // or under a low ceiling the head's block can be one (Nov 18: cast at midday at a
        // tunnel's mouth)
        int feet = light(world, x, (int) Math.floor(position.y() + 0.2), z, time);
        int head = light(world, x, (int) Math.floor(position.y() + 1.5), z, time);
        return Math.max(feet, head) <= DARK_LIGHT;
    }

    /**
     * @return what {@link #dark} reads where the player stands, for {@code /mt info} and the
     * log: each block's light, its sky light, the sun factor and its block light
     */
    public static String describeLight(Ref<EntityStore> ref, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (transform == null || time == null) {
            return "Light: unknown (no position or time)";
        }
        World world = store.getExternalData().getWorld();
        Vector3d position = transform.getPosition();
        int x = (int) Math.floor(position.x());
        int z = (int) Math.floor(position.z());
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "Light (sun %.2f, hour %d): ",
                time.getSunlightFactor(), time.getCurrentHour()));
        int[] ys = {(int) Math.floor(position.y() + 0.2), (int) Math.floor(position.y() + 1.5)};
        String[] names = {"feet", "head"};
        for (int i = 0; i < 2; i++) {
            int y = ys[i];
            text.append(names[i]).append(" y").append(y).append(' ');
            Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
            BlockSection blocks = section == null || !section.isValid() ? null
                    : world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
            if (blocks == null) {
                text.append("not loaded");
            } else {
                ChunkLightData light = blocks.getGlobalLight();
                text.append(light(world, x, y, z, time)).append(" (sky ").append(light.getSkyLight(x, y, z))
                        .append(", block ").append(light.getBlockLightIntensity(x, y, z))
                        .append(blocks.hasGlobalLight() ? "" : ", light not worked out").append(')');
            }
            text.append(i == 0 ? "; " : "");
        }
        return text.append(dark(ref, store) ? ": dark" : ": not dark").append(", Cat Sight casts at ")
                .append(DARK_LIGHT).append(" or less").toString();
    }

    /**
     * @return the light at a block as {@link #dark} reads it, or 15 (not dark) if the block's
     * section isn't loaded or its light isn't worked out yet: an unlit section reads all 0
     */
    private static int light(World world, int x, int y, int z, WorldTimeResource time) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return 15;
        }
        BlockSection blocks = world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null || !blocks.hasGlobalLight()) {
            return 15;
        }
        ChunkLightData light = blocks.getGlobalLight();
        int sky = (int) (light.getSkyLight(x, y, z) * time.getSunlightFactor());
        return Math.max(sky, light.getBlockLightIntensity(x, y, z));
    }

    /**
     * @return true if the player is alive and below {@link #BARRIER_HEALTH} of their health
     */
    private static boolean lowHealth(Ref<EntityStore> ref, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
        return value != null && value.get() > value.getMin()
                && value.get() < value.getMax() * BARRIER_HEALTH;
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
     * A Barrier: when a hit leaves the wearer below {@link #BARRIER_HEALTH}, starts its ward
     * and, if the spell has an {@code Effect}, heals that much (scaled by the piece). The base
     * Barrier has none; the Amulet of the Last Stand heals 30 (v1.2, Mark Nov 18: a ward alone
     * at low health only put off the next hit or two).
     *
     * @return true if the ward started
     */
    private static boolean barrier(Spell spell, SpellFactors factors, Ref<EntityStore> ref,
                                   Store<EntityStore> store) {
        if (!lowHealth(ref, store) || buff(spell, factors, ref, store) == null) {
            return false;
        }
        if (spell.getEffect() > 0) {
            heal(ref, store, spell.getEffect() * factors.getEffect());
        }
        return true;
    }

    /**
     * The Curse of Weariness: takes {@link #WEARINESS_STAMINA} from the wearer's stamina, down
     * to 0 at most (vanilla lets stamina go below 0, an overdrawn state; the curse doesn't).
     */
    private static void weary(Ref<EntityStore> ref, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return;
        }
        int stamina = DefaultEntityStatTypes.getStamina();
        EntityStatValue value = stats.get(stamina);
        if (value == null || value.get() <= 0) {
            return;
        }
        stats.addStatValue(stamina, (float) -Math.min(WEARINESS_STAMINA, value.get()));
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
