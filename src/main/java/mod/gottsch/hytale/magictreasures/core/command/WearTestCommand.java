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
package mod.gottsch.hytale.magictreasures.core.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * /mt wear - feasibility test: shows a piece of jewelry (by default the vanilla necklace cosmetic) on the
 * calling player's model. Run it again to put the original model back.
 * <p>
 * The server's player model is a plain body with a stock face (5 attachments: face, ears, mouth, eyes,
 * eyebrows in default colours). The client draws the player's real look from the {@link PlayerSkin} only
 * until the server sends a changed model; after that it draws exactly what it is sent. So this rebuilds
 * the whole look from the skin's parts ({@link CosmeticRegistry}): the body's texture and skin tone on the
 * model, then the face parts and outfit as {@link ModelAttachment}s, then the jewelry.
 * {@code --outfit=false} keeps the model's own attachments instead (plain body, stock face).
 * The original model is kept per player and restored on the next run. Every run logs what it sends.
 * <p>
 * A skin stores each part as {@code PartId.ColorOrTexture[.Variant]}. A part either has a texture per
 * colour, or a greyscale texture tinted with its gradient set.
 *
 * @author Mark Gottschling on 11/08/2026
 */
public class WearTestCommand extends AbstractPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Each wearer's model from before the first /mt wear, put back by the next one. */
    private static final Map<UUID, Model> ORIGINALS = new ConcurrentHashMap<>();

    private final DefaultArg<String> modelArg;
    private final DefaultArg<String> textureArg;
    private final DefaultArg<Boolean> outfitArg;
    private final DefaultArg<Boolean> faceArg;
    private final DefaultArg<Boolean> listArg;

    public WearTestCommand() {
        super("wear", "Test: toggle a piece of jewelry (default: a necklace) on yourself.");
        this.modelArg = withDefaultArg("model", "Jewelry .blockymodel under Common/",
                ArgTypes.STRING, "Cosmetics/Overtops/Overtops_Necklace.blockymodel",
                "Cosmetics/Overtops/Overtops_Necklace.blockymodel");
        this.textureArg = withDefaultArg("texture", "Jewelry texture under Common/",
                ArgTypes.STRING, "Cosmetics/Overtops/Overtops_Necklace_Textures/Necklace_2.png",
                "Cosmetics/Overtops/Overtops_Necklace_Textures/Necklace_2.png");
        this.outfitArg = withDefaultArg("outfit", "Rebuild the whole look from the skin (false: keep the model's own attachments)",
                ArgTypes.BOOLEAN, true, "true");
        this.faceArg = withDefaultArg("face", "Also rebuild face, eyes, ears and mouth",
                ArgTypes.BOOLEAN, true, "true");
        this.listArg = withDefaultArg("list", "Only list the model's attachments and the skin's parts",
                ArgTypes.BOOLEAN, false, "false");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        if (modelComponent == null || modelComponent.getModel() == null) {
            context.sendMessage(Message.raw("The player has no model component."));
            return;
        }
        Model model = modelComponent.getModel();
        ModelAttachment[] current = model.getAttachments() == null ? new ModelAttachment[0] : model.getAttachments();
        PlayerSkinComponent skinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        PlayerSkin skin = skinComponent == null ? null : skinComponent.getPlayerSkin();

        LOGGER.atInfo().log("wear: model %s, texture %s | %s | %s, %d attachments, body %s, skin %s",
                model.getModelAssetId(), model.getTexture(), model.getGradientSet(), model.getGradientId(), current.length,
                skin == null ? "none" : skin.bodyCharacteristic, skin == null ? "none" : parts(skin, true).toString());
        for (ModelAttachment a : current) {
            LOGGER.atInfo().log("wear: has %s | %s | %s | %s", a.getModel(), a.getTexture(), a.getGradientSet(), a.getGradientId());
        }
        if (listArg.get(context)) {
            context.sendMessage(Message.raw("Model " + model.getModelAssetId() + " (" + model.getModel()
                    + "), texture " + model.getTexture() + ", " + current.length + " attachments:"));
            for (ModelAttachment attachment : current) {
                context.sendMessage(Message.raw("  " + attachment.getModel() + " | " + attachment.getTexture()));
            }
            if (skin != null) {
                parts(skin, true).forEach((slot, id) -> context.sendMessage(Message.raw("  skin " + slot + " = " + id)));
            }
            return;
        }

        // wearing our jewelry -> put the original model back
        UUID uuid = playerRef.getUuid();
        Model original = ORIGINALS.remove(uuid);
        if (original != null) {
            store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(original));
            context.sendMessage(Message.raw("Back to your original model (" + original.getAttachments().length + " attachments)."));
            return;
        }
        ORIGINALS.put(uuid, model);

        List<ModelAttachment> attachments = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String bodyTexture = model.getTexture();
        String bodyGradientSet = model.getGradientSet();
        String bodyGradientId = model.getGradientId();
        if (outfitArg.get(context) && skin != null) {
            // the server's model is a plain body with a stock face; the client draws the real look from the
            // skin only until the model changes, so rebuild all of it: body, face parts and outfit
            CosmeticRegistry registry = CosmeticsModule.get().getRegistry();
            String[] body = skin.bodyCharacteristic == null ? new String[0] : skin.bodyCharacteristic.split("\\.");
            String tone = body.length > 1 ? body[1] : model.getGradientId();
            PlayerSkinPart bodyPart = body.length > 0 ? registry.getBodyCharacteristics().get(body[0]) : null;
            if (bodyPart != null) {
                bodyTexture = bodyPart.getGreyscaleTexture();
                bodyGradientSet = bodyPart.getGradientSet();
            } else {
                problems.add("bodyCharacteristic=" + skin.bodyCharacteristic);
            }
            bodyGradientId = tone;
            parts(skin, faceArg.get(context)).forEach((slot, id) -> {
                ModelAttachment attachment = toAttachment(registry, slot, id, tone);
                if (attachment == null) {
                    problems.add(slot + "=" + id);
                } else {
                    attachments.add(attachment);
                }
            });
        } else {
            // keep the attachments the model already has
            attachments.addAll(List.of(current));
        }
        attachments.add(new ModelAttachment(modelArg.get(context), textureArg.get(context), null, null, 1.0));

        for (ModelAttachment a : attachments) {
            LOGGER.atInfo().log("wear: sending %s | %s | %s | %s", a.getModel(), a.getTexture(), a.getGradientSet(), a.getGradientId());
        }
        if (!problems.isEmpty()) {
            LOGGER.atInfo().log("wear: could not resolve %s", problems);
        }
        LOGGER.atInfo().log("wear: body %s | %s | %s", bodyTexture, bodyGradientSet, bodyGradientId);
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(copyWith(model,
                attachments.toArray(new ModelAttachment[0]), bodyTexture, bodyGradientSet, bodyGradientId)));
        context.sendMessage(Message.raw("Sent " + attachments.size() + " attachments (outfit + jewelry). Run again to undo."));
        if (!problems.isEmpty()) {
            context.sendMessage(Message.raw("Could not resolve: " + String.join(", ", problems)));
        }
    }

    /**
     * The skin's filled-in slots, in drawing order. Face parts are optional because the client may
     * still draw those itself.
     */
    private static Map<String, String> parts(PlayerSkin skin, boolean withFace) {
        Map<String, String> parts = new LinkedHashMap<>();
        if (withFace) {
            put(parts, "face", skin.face);
            put(parts, "eyes", skin.eyes);
            put(parts, "ears", skin.ears);
            put(parts, "mouth", skin.mouth);
        }
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

    /**
     * One skin part as an attachment: {@code PartId.ColorOrTexture[.Variant]}. Uses the variant's model and
     * textures when there is one, the texture for the colour when the part has one, and otherwise the
     * greyscale texture tinted with the part's gradient set.
     */
    private static ModelAttachment toAttachment(CosmeticRegistry registry, String slot, String id, String tone) {
        String[] bits = id.split("\\.");
        PlayerSkinPart part = SLOT_PARTS.get(slot).apply(registry).get(bits[0]);
        if (part == null) {
            return null;
        }
        String color = bits.length > 1 ? bits[1] : null;
        String model = part.getModel();
        Map<String, PlayerSkinPartTexture> textures = part.getTextures();
        String greyscale = part.getGreyscaleTexture();
        if (bits.length > 2 && part.getVariants() != null && part.getVariants().containsKey(bits[2])) {
            PlayerSkinPart.Variant variant = part.getVariants().get(bits[2]);
            model = variant.getModel();
            textures = variant.getTextures() != null ? variant.getTextures() : textures;
            greyscale = variant.getGreyscaleTexture() != null ? variant.getGreyscaleTexture() : greyscale;
        }
        if (model == null) {
            return null;
        }
        if (color != null && textures != null && textures.containsKey(color)) {
            return new ModelAttachment(model, textures.get(color).getTexture(), null, null, 1.0);
        }
        // skin-coloured parts (face, ears, mouth) have no colour of their own: they take the body's skin tone
        if (color == null && CosmeticRegistry.SKIN_GRADIENTSET_ID.equals(part.getGradientSet())) {
            color = tone;
        }
        return new ModelAttachment(model, greyscale, part.getGradientSet(), color, 1.0);
    }

    /**
     * A copy of the model with different attachments and body texture/tint; every other value is kept.
     */
    private static Model copyWith(Model m, ModelAttachment[] attachments, String texture, String gradientSet, String gradientId) {
        return new Model(m.getModelAssetId(), m.getScale(), m.getRandomAttachmentIds(), attachments,
                m.getBoundingBox(), m.getModel(), texture, gradientSet, gradientId,
                m.getEyeHeight(), m.getCrouchOffset(), m.getSittingOffset(), m.getSleepingOffset(),
                m.getAnimationSetMap(), m.getCamera(), m.getLight(), m.getParticles(), m.getTrails(),
                m.getPhysicsValues(), m.getDetailBoxes(), m.getPhobia(), m.getPhobiaModelAssetId());
    }
}
