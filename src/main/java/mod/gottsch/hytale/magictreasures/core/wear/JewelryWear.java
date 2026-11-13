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
import mod.gottsch.hytale.magictreasures.core.element.ElementWear;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each player's worn jewelry and their look in step with their Treasures Case.
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
 * Rings, necklaces and bracelets are visible (art Nov 10), in the colours of their item
 * textures. Rings sit on a finger (the first worn ring right, the second left), mostly inside
 * gloves; a bracelet sits on the left wrist.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class JewelryWear {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();


    /**
     * Worn rings and necklaces (Nov 10, tools/make_ring_art.py and make_necklace_art.py). A ring
     * is a thin band around the hand, inside a glove: the first worn ring on the right hand, the
     * second on the left. A necklace is a chain decal on the chest (the vanilla necklace's quads)
     * with a 3D pendant and its gem, model {@code NECKLACE_WORN + style [+ "_Set"]}.
     * <p>
     * Both use the ring item textures ({@code Ring_<Material>[_<Gem>].png}), in their own colours.
     * The client refuses an attachment texture that no asset references ("Cannot use ... as an
     * entity attachment texture"), so a worn-only greyscale texture tinted by the material's
     * gradient never showed; the jewelry items reference these.
     */
    public static final String RING_MODEL_RIGHT = "Cosmetics/MagicTreasures/Ring_Worn_R.blockymodel";
    public static final String RING_MODEL_LEFT = "Cosmetics/MagicTreasures/Ring_Worn_L.blockymodel";
    public static final String NECKLACE_WORN = "Cosmetics/MagicTreasures/Necklace_Worn_";
    /** A worn bracelet, on the left wrist (tools/make_bracelet_art.py): {@code + style [+ "_Set"]}. */
    public static final String BRACELET_WORN = "Cosmetics/MagicTreasures/Bracelet_Worn_";
    public static final String ITEM_TEXTURES = "Items/MagicTreasures/Rings/Ring_";
    /** The metals with ring textures. */
    public static final Set<String> ART_MATERIALS = Set.of("Copper", "Silver", "Gold");
    /**
     * Metals with worn-ring art only (Nov 13: Bone, for the Vampire Lich's Ring; no Bone
     * necklace or bracelet art yet).
     */
    public static final Set<String> RING_ONLY_MATERIALS = Set.of("Bone");
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
        int rings = 0;
        for (ItemStack piece : jewelry.pieces()) {
            // the first ring goes on the right hand, the second on the left
            JewelryDefinition definition = Jewelry.definition(piece);
            boolean ring = definition != null && "Ring".equalsIgnoreCase(definition.getType());
            List<ModelAttachment> attachments = wornModels(piece, ring ? rings++ : 0);
            if (!attachments.isEmpty()) {
                visible.addAll(attachments);
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
        ElementWear.update(uuid, jewelry);
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
        ElementWear.onLeave(uuid);
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
     * The attachments that show a worn piece: one per piece, textured with its item texture.
     *
     * @param ringIndex for a ring, 0 for the first worn ring (right hand), 1 for the second (left)
     * @return empty if the piece isn't shown
     */
    public static List<ModelAttachment> wornModels(ItemStack piece, int ringIndex) {
        JewelryStats stats = JewelryStats.of(piece.getItemId());
        if (!stats.known()) {
            return List.of();
        }
        JewelryDefinition definition = stats.definition();
        String type = definition.getType();
        String metal = definition.getMaterial();
        boolean ring = "Ring".equalsIgnoreCase(type);
        if (!ART_MATERIALS.contains(metal) && !(ring && RING_ONLY_MATERIALS.contains(metal))) {
            // no art for this metal yet (iron; bone but for rings): not shown
            return List.of();
        }
        boolean necklace = "Necklace".equalsIgnoreCase(type);
        if (necklace || "Bracelet".equalsIgnoreCase(type)) {
            // the starter art has Regular and Great; other styles use Great's for now
            String style = "Regular".equalsIgnoreCase(definition.getStyle()) ? "Regular" : "Great";
            String gem = definition.getGem();
            String texture = ITEM_TEXTURES + metal + (gem == null ? "" : "_" + gem) + ".png";
            String model = (necklace ? NECKLACE_WORN : BRACELET_WORN) + style + (gem == null ? "" : "_Set")
                    + ".blockymodel";
            return List.of(new ModelAttachment(model, texture, null, null, 1.0));
        }
        if ("Ring".equalsIgnoreCase(type)) {
            return List.of(new ModelAttachment(ringIndex == 0 ? RING_MODEL_RIGHT : RING_MODEL_LEFT,
                    ITEM_TEXTURES + metal + ".png", null, null, 1.0));
        }
        return List.of();
    }
}
