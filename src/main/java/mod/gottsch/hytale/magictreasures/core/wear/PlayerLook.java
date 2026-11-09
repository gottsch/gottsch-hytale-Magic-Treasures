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

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds a player's whole look as a server {@link Model}, so that extra attachments (worn
 * jewelry) can be added to it.
 * <p>
 * The server's player model is a plain body with a stock face. The client draws the
 * player's real look from their {@link PlayerSkin} only until the server sends a changed
 * model; after that it draws exactly what it is sent. So a model with jewelry must carry
 * the whole look: the body's texture and skin tone, then every face part and outfit part
 * from the skin as {@link ModelAttachment}s, then the jewelry. Taking jewelry off means
 * sending the same look without it. This was worked out with the {@code /mt wear}
 * feasibility test (Nov 8, 2026).
 * <p>
 * A skin stores each part as {@code PartId.ColorOrTexture[.Variant]}, resolved through the
 * {@link CosmeticRegistry}. A part has either a texture per colour, or a greyscale texture
 * tinted with its gradient set. Skin-coloured parts (face, ears, mouth) take the body's
 * tone.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public final class PlayerLook {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<String, Function<CosmeticRegistry, Map<String, PlayerSkinPart>>> SLOT_PARTS = Map.ofEntries(
            Map.entry("face", CosmeticRegistry::getFaces),
            Map.entry("eyes", CosmeticRegistry::getEyes),
            Map.entry("ears", CosmeticRegistry::getEars),
            Map.entry("mouth", CosmeticRegistry::getMouths),
            Map.entry("underwear", CosmeticRegistry::getUnderwear),
            Map.entry("skinFeature", CosmeticRegistry::getSkinFeatures),
            Map.entry("eyebrows", CosmeticRegistry::getEyebrows),
            Map.entry("facialHair", CosmeticRegistry::getFacialHairs),
            Map.entry("haircut", CosmeticRegistry::getHaircuts),
            Map.entry("pants", CosmeticRegistry::getPants),
            Map.entry("overpants", CosmeticRegistry::getOverpants),
            Map.entry("undertop", CosmeticRegistry::getUndertops),
            Map.entry("overtop", CosmeticRegistry::getOvertops),
            Map.entry("shoes", CosmeticRegistry::getShoes),
            Map.entry("gloves", CosmeticRegistry::getGloves),
            Map.entry("headAccessory", CosmeticRegistry::getHeadAccessories),
            Map.entry("faceAccessory", CosmeticRegistry::getFaceAccessories),
            Map.entry("earAccessory", CosmeticRegistry::getEarAccessories),
            Map.entry("cape", CosmeticRegistry::getCapes));

    private PlayerLook() {
    }

    /**
     * @param model the player's current model, used for everything but the look (its id,
     *              bounding box, camera, animations, ...)
     * @param skin the player's skin; with none, the model's own attachments are kept
     * @param extras attachments to add on top of the look, e.g. worn jewelry
     * @return a copy of the model wearing the skin's whole look plus the extras
     */
    public static Model build(Model model, @Nullable PlayerSkin skin, List<ModelAttachment> extras) {
        List<ModelAttachment> attachments = new ArrayList<>();
        String bodyTexture = model.getTexture();
        String bodyGradientSet = model.getGradientSet();
        String bodyGradientId = model.getGradientId();
        if (skin != null) {
            CosmeticRegistry registry = CosmeticsModule.get().getRegistry();
            String[] body = skin.bodyCharacteristic == null ? new String[0] : skin.bodyCharacteristic.split("\\.");
            String tone = body.length > 1 ? body[1] : model.getGradientId();
            PlayerSkinPart bodyPart = body.length > 0 ? registry.getBodyCharacteristics().get(body[0]) : null;
            if (bodyPart != null) {
                bodyTexture = bodyPart.getGreyscaleTexture();
                bodyGradientSet = bodyPart.getGradientSet();
            }
            bodyGradientId = tone;
            List<String> problems = new ArrayList<>();
            parts(skin).forEach((slot, id) -> {
                ModelAttachment attachment = toAttachment(registry, slot, id, tone);
                if (attachment == null) {
                    problems.add(slot + "=" + id);
                } else {
                    attachments.add(attachment);
                }
            });
            if (!problems.isEmpty()) {
                LOGGER.atWarning().log("Could not resolve skin parts %s", problems);
            }
        } else if (model.getAttachments() != null) {
            attachments.addAll(List.of(model.getAttachments()));
        }
        attachments.addAll(extras);
        return copyWith(model, attachments.toArray(new ModelAttachment[0]), bodyTexture, bodyGradientSet, bodyGradientId);
    }

    /**
     * The skin's filled-in slots, in drawing order.
     */
    private static Map<String, String> parts(PlayerSkin skin) {
        Map<String, String> parts = new LinkedHashMap<>();
        put(parts, "face", skin.face);
        put(parts, "eyes", skin.eyes);
        put(parts, "ears", skin.ears);
        put(parts, "mouth", skin.mouth);
        put(parts, "underwear", skin.underwear);
        put(parts, "skinFeature", skin.skinFeature);
        put(parts, "eyebrows", skin.eyebrows);
        put(parts, "facialHair", skin.facialHair);
        put(parts, "haircut", skin.haircut);
        put(parts, "pants", skin.pants);
        put(parts, "overpants", skin.overpants);
        put(parts, "undertop", skin.undertop);
        put(parts, "overtop", skin.overtop);
        put(parts, "shoes", skin.shoes);
        put(parts, "gloves", skin.gloves);
        put(parts, "headAccessory", skin.headAccessory);
        put(parts, "faceAccessory", skin.faceAccessory);
        put(parts, "earAccessory", skin.earAccessory);
        put(parts, "cape", skin.cape);
        return parts;
    }

    private static void put(Map<String, String> parts, String slot, String id) {
        if (id != null && !id.isEmpty()) {
            parts.put(slot, id);
        }
    }

    /**
     * One skin part as an attachment: {@code PartId.ColorOrTexture[.Variant]}. Uses the
     * variant's model and textures when there is one, the texture for the colour when the
     * part has one, and otherwise the greyscale texture tinted with the part's gradient set.
     */
    @Nullable
    private static ModelAttachment toAttachment(CosmeticRegistry registry, String slot, String id, String tone) {
        String[] bits = id.split("\\.");
        PlayerSkinPart part = SLOT_PARTS.get(slot).apply(registry).get(bits[0]);
        if (part == null) {
            return null;
        }
        String color = bits.length > 1 ? bits[1] : null;
        String partModel = part.getModel();
        Map<String, PlayerSkinPartTexture> textures = part.getTextures();
        String greyscale = part.getGreyscaleTexture();
        if (bits.length > 2 && part.getVariants() != null && part.getVariants().containsKey(bits[2])) {
            PlayerSkinPart.Variant variant = part.getVariants().get(bits[2]);
            partModel = variant.getModel();
            textures = variant.getTextures() != null ? variant.getTextures() : textures;
            greyscale = variant.getGreyscaleTexture() != null ? variant.getGreyscaleTexture() : greyscale;
        }
        if (partModel == null) {
            return null;
        }
        if (color != null && textures != null && textures.containsKey(color)) {
            return new ModelAttachment(partModel, textures.get(color).getTexture(), null, null, 1.0);
        }
        // skin-coloured parts (face, ears, mouth) have no colour of their own: they take the body's skin tone
        if (color == null && CosmeticRegistry.SKIN_GRADIENTSET_ID.equals(part.getGradientSet())) {
            color = tone;
        }
        return new ModelAttachment(partModel, greyscale, part.getGradientSet(), color, 1.0);
    }

    /**
     * A copy of the model with different attachments and body texture and tint; every other
     * value is kept.
     */
    private static Model copyWith(Model m, ModelAttachment[] attachments, String texture, String gradientSet,
                                  String gradientId) {
        return new Model(m.getModelAssetId(), m.getScale(), m.getRandomAttachmentIds(), attachments,
                m.getBoundingBox(), m.getModel(), texture, gradientSet, gradientId,
                m.getEyeHeight(), m.getCrouchOffset(), m.getSittingOffset(), m.getSleepingOffset(),
                m.getAnimationSetMap(), m.getCamera(), m.getLight(), m.getParticles(), m.getTrails(),
                m.getPhysicsValues(), m.getDetailBoxes(), m.getPhobia(), m.getPhobiaModelAssetId());
    }
}
