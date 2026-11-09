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
package mod.gottsch.hytale.magictreasures.core.wear;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryMaterial;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each player's worn jewelry and their look in step with their Jewelry Case.
 * <p>
 * {@link #update} reads what is worn ({@link WornJewelry}) and, when the visible pieces
 * changed, sends a new look: the player's whole skin rebuilt ({@link PlayerLook}) plus a
 * model for each visible piece. A player who has never worn visible jewelry keeps the
 * game's own model, so the client keeps drawing their skin itself.
 * <p>
 * It is called when the inventory changes ({@link WornJewelrySystem}) and when a player is
 * ready after joining. The look has to be sent again at join because the server rebuilds the
 * player's model then. Armor changes and respawning keep it (tested Nov 9, 2026).
 * <p>
 * Only necklaces are visible for now. A ring is too small to see, and bracelets would clash
 * with gloves. Until there is jewelry art, a necklace shows as the vanilla heart necklace,
 * tinted with its material's gradient (for example {@code Ornamented_Metal} /
 * {@code Gold_Red}).
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class JewelryWear {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Placeholder worn model until there is jewelry art. */
    public static final String NECKLACE_MODEL = "Cosmetics/Overtops/Overtops_Necklace.blockymodel";
    public static final String NECKLACE_TEXTURE = "Cosmetics/Overtops/Overtops_Necklace_Textures/Necklace_1_Greyscale.png";

    /** What each online player wears, and the visible pieces last sent (null: the look was never changed). */
    private static final Map<UUID, Worn> WORN = new ConcurrentHashMap<>();

    private record Worn(WornJewelry jewelry, @Nullable List<String> shown) {
    }

    private JewelryWear() {
    }

    /**
     * @return what the player was wearing at the last update, or {@link WornJewelry#NONE}
     */
    public static WornJewelry worn(UUID uuid) {
        Worn worn = WORN.get(uuid);
        return worn == null ? WornJewelry.NONE : worn.jewelry();
    }

    /**
     * Re-reads what the player wears and sends a new look if the visible pieces changed.
     *
     * @param force send the look even if nothing visible changed (after joining, or from
     *              {@code /mt worn --refresh})
     */
    public static void update(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, boolean force) {
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        WornJewelry jewelry = WornJewelry.of(ref, accessor);
        List<ModelAttachment> visible = new ArrayList<>();
        List<String> visibleIds = new ArrayList<>();
        for (ItemStack piece : jewelry.pieces()) {
            ModelAttachment attachment = wornModel(piece);
            if (attachment != null) {
                visible.add(attachment);
                visibleIds.add(piece.getItemId());
            }
        }

        Worn previous = WORN.get(uuid);
        List<String> shown = previous == null ? null : previous.shown();
        boolean changed = shown == null ? !visibleIds.isEmpty() : !shown.equals(visibleIds);
        // never touch the look of a player who has never worn visible jewelry: the client draws their skin
        boolean send = changed || (force && (shown != null || !visibleIds.isEmpty()));
        if (send && sendLook(ref, accessor, visible)) {
            shown = List.copyOf(visibleIds);
        }
        WORN.put(uuid, new Worn(jewelry, shown));
        if (previous == null || !previous.jewelry().itemIds().equals(jewelry.itemIds())) {
            LOGGER.atInfo().log("%s wears %s (case: %s)%s", playerRef.getUsername(), jewelry.itemIds(),
                    jewelry.caseLocation(), send ? ", look sent" : "");
        }
    }

    /**
     * After joining, the server has rebuilt the player's model, so whatever was shown before is
     * gone. Forget it and show the jewelry again.
     */
    public static void onJoin(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            WORN.remove(playerRef.getUuid());
        }
        update(ref, accessor, false);
    }

    public static void onLeave(UUID uuid) {
        WORN.remove(uuid);
    }

    private static boolean sendLook(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                                    List<ModelAttachment> visible) {
        ModelComponent modelComponent = accessor.getComponent(ref, ModelComponent.getComponentType());
        if (modelComponent == null || modelComponent.getModel() == null) {
            return false;
        }
        PlayerSkinComponent skinComponent = accessor.getComponent(ref, PlayerSkinComponent.getComponentType());
        PlayerSkin skin = skinComponent == null ? null : skinComponent.getPlayerSkin();
        Model look = PlayerLook.build(modelComponent.getModel(), skin, visible);
        accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(look));
        return true;
    }

    /**
     * @return the model shown on the player for a worn piece, or null if it isn't visible
     */
    @Nullable
    public static ModelAttachment wornModel(ItemStack piece) {
        JewelryStats stats = JewelryStats.of(piece.getItemId());
        if (!stats.known() || !"Necklace".equalsIgnoreCase(stats.definition().getType())) {
            return null;
        }
        JewelryMaterial material = stats.material();
        return new ModelAttachment(NECKLACE_MODEL, NECKLACE_TEXTURE, material.getGradientSet(), material.getGradient(), 1.0);
    }
}
