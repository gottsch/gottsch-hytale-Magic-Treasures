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
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.system.AbilityCastSnapshotSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.SpellFactors;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Boosts a rune cast from the player's ability slots when a worn piece of jewelry holds the
 * same rune (decision 4, changed Nov 9: the client has only 2 ability lines, so jewelry
 * boosts the bench's runes instead of adding its own).
 * <p>
 * Vanilla's {@code AbilityCastSnapshotSystem} puts the cast's resolved attributes on the
 * chain's context ({@link ImpactModifiers#CAST_SNAPSHOT}) when an ability chain starts: the
 * core rune's attributes, multiplied by its support runes. This system runs right after it
 * and multiplies in the first worn piece holding that rune:
 * <ul>
 *     <li>{@code Cost}: the mana cost multiplier (vanilla reads it with a fallback of 1)</li>
 *     <li>{@code Cooldown}: the cooldown multiplier (fallback 1)</li>
 *     <li>{@code Damage}: the damage multiplier (fallback 1), scaled by the Effect factor</li>
 *     <li>{@code Duration}: only when the rune declares one (it is in seconds, not a
 *     multiplier)</li>
 * </ul>
 * Each boosted cast uses one of the piece's uses. Runes still spend the player's mana
 * (decision 3), not the piece's charge.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class RuneBoostSystem extends EntityEventSystem<EntityStore, InteractionChainStartEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The held-item section id of a cast from the ability slots (vanilla checks the same). */
    public static final int ABILITY_SECTION_ID = -11;

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
            new SystemDependency<>(Order.AFTER, AbilityCastSnapshotSystem.class));

    public RuneBoostSystem() {
        super(InteractionChainStartEvent.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InteractionChainStartEvent event) {
        if (event.isCancelled()) {
            return;
        }
        InteractionContext context = event.getContext();
        if (context.getHeldItemSectionId() != ABILITY_SECTION_ID) {
            return;
        }
        ItemStack rune = context.getHeldItem();
        if (rune == null || rune.isEmpty() || !(rune.getItem().getAbility() instanceof CoreItemAbility ability)) {
            return;
        }
        String runeId = rune.getItemId();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        WornJewelry worn = WornJewelry.of(ref, commandBuffer);
        int match = -1;
        for (int i = 0; i < worn.pieces().size(); i++) {
            JewelryState state = Jewelry.state(worn.pieces().get(i));
            if (state != null && Arrays.asList(state.getRunes()).contains(runeId) && state.getUses() > 0) {
                match = i;
                break;
            }
        }
        ImpactModifiers snapshot = context.getMetaStore().getIfPresentMetaObject(ImpactModifiers.CAST_SNAPSHOT);
        Map<String, Float> before = new HashMap<>();
        if (snapshot != null && snapshot.getAttributes() != null) {
            before.putAll(snapshot.getAttributes());
        } else if (ability.getAttributes() != null) {
            before.putAll(ability.getAttributes());
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        boolean report = playerRef != null && HitMeter.SHOW_HITS.contains(playerRef.getUuid());

        ItemStack piece = match < 0 ? null : worn.pieces().get(match);
        JewelryStats stats = piece == null ? null : Jewelry.stats(piece);
        if (piece == null || !stats.known()) {
            if (report) {
                report(playerRef, ref, store, ability, runeId, before, before, null);
            }
            return;
        }
        SpellFactors factors = stats.factors();
        Map<String, Float> attributes = new HashMap<>(before);
        multiply(attributes, "Cost", factors.getCost());
        multiply(attributes, "Cooldown", factors.getCooldown());
        multiply(attributes, "Damage", factors.getEffect());
        if (attributes.containsKey("Duration")) {
            multiply(attributes, "Duration", factors.getDuration());
        }
        context.getMetaStore().putMetaObject(ImpactModifiers.CAST_SNAPSHOT, new ImpactModifiers(attributes,
                snapshot == null ? null : snapshot.getOnHitEffectIds(),
                snapshot == null ? null : snapshot.getElementalCauseId()));

        if (report) {
            report(playerRef, ref, store, ability, runeId, before, attributes, Jewelry.displayName(piece));
        }
        LOGGER.atInfo().log("%s boosted by %s: %s -> %s", runeId, piece.getItemId(), before, attributes);

        // spend a use on the world thread, after the systems, since it rewrites the case
        short slot = worn.pieceSlots().get(match);
        String pieceId = piece.getItemId();
        store.getExternalData().getWorld().execute(() -> {
            if (ref.isValid()) {
                SpellEngine.useRune(ref, store, slot, pieceId);
            }
        });
    }

    /**
     * For {@code /mt hits}: prints the cast's mana cost, cooldown and damage multiplier before
     * and after the jewelry boost, worked out the way vanilla reads them (the rune's declared
     * cost and cooldown times the {@code Cost} and {@code Cooldown} attributes). Then, half a
     * second later, prints the mana actually spent, measured on the player's mana bar.
     *
     * @param boostedBy the boosting piece's name, or null if no worn piece holds this rune
     */
    private static void report(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store,
                               CoreItemAbility ability, String runeId, Map<String, Float> before,
                               Map<String, Float> after, @Nullable String boostedBy) {
        String name = runeId.replace("Rune_", "");
        float costBefore = ability.getCost() * before.getOrDefault("Cost", 1f);
        float costAfter = ability.getCost() * after.getOrDefault("Cost", 1f);
        float cooldownBefore = ability.getCooldownS() * before.getOrDefault("Cooldown", 1f);
        float cooldownAfter = ability.getCooldownS() * after.getOrDefault("Cooldown", 1f);
        String text = boostedBy == null
                ? String.format("%s: cost %.1f mana, cooldown %.2f s, damage x%.2f (no jewelry boost)",
                        name, costBefore, cooldownBefore, before.getOrDefault("Damage", 1f))
                : String.format("%s: cost %.1f -> %.1f mana, cooldown %.2f -> %.2f s, damage x%.2f -> x%.2f (%s)",
                        name, costBefore, costAfter, cooldownBefore, cooldownAfter,
                        before.getOrDefault("Damage", 1f), after.getOrDefault("Damage", 1f), boostedBy);
        playerRef.sendMessage(Message.raw(text).color("#9fd3ff"));

        // measure the mana actually spent: the bar now, then again once the cast has charged it
        float manaNow = mana(ref, store);
        if (Float.isNaN(manaNow)) {
            return;
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                store.getExternalData().getWorld().execute(() -> {
                    float manaLater = ref.isValid() ? mana(ref, store) : Float.NaN;
                    if (!Float.isNaN(manaLater)) {
                        playerRef.sendMessage(Message.raw(String.format(
                                "  mana %.1f -> %.1f: spent about %.1f (regen adds about 1.7 per half second)",
                                manaNow, manaLater, manaNow - manaLater)).color("#9fd3ff"));
                    }
                });
            } catch (IllegalThreadStateException e) {
                // the world shut down
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    private static float mana(Ref<EntityStore> ref, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue mana = stats == null ? null : stats.get(DefaultEntityStatTypes.getMana());
        return mana == null ? Float.NaN : mana.get();
    }

    private static void multiply(Map<String, Float> attributes, String key, double factor) {
        if (factor != 1.0) {
            attributes.put(key, (float) (attributes.getOrDefault(key, 1f) * factor));
        }
    }
}
