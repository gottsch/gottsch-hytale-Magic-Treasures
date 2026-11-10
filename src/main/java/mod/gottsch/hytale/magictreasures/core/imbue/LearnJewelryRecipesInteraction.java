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
package mod.gottsch.hytale.magictreasures.core.imbue;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * A recipe scroll's interaction: teaches every crafting recipe for one jewelry type, as the
 * Minecraft recipe scrolls did (decision, Mark Nov 10: learned recipes). Vanilla's
 * {@code LearnRecipe} teaches one item; this teaches all plain pieces of the type, the ones
 * whose item JSON has a recipe: {@code "Type": "MT_LearnJewelryRecipes", "JewelryType": "Ring"}.
 * <p>
 * If nothing new was learned the interaction fails, so a following
 * {@code ModifyInventory} doesn't use up the scroll.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class LearnJewelryRecipesInteraction extends SimpleInstantInteraction {
    /** The type name used in item JSON. */
    public static final String TYPE_NAME = "MT_LearnJewelryRecipes";

    public static final BuilderCodec<LearnJewelryRecipesInteraction> CODEC = BuilderCodec
            .builder(LearnJewelryRecipesInteraction.class, LearnJewelryRecipesInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .documentation("Teaches the crafting recipes of every plain piece of one jewelry type.")
            .append(new KeyedCodec<>("JewelryType", Codec.STRING),
                    (interaction, value, extraInfo) -> interaction.jewelryType = value,
                    (interaction, extraInfo) -> interaction.jewelryType)
            .documentation("Ring, Necklace or Bracelet.")
            .add()
            .build();

    private String jewelryType = "Ring";

    public LearnJewelryRecipesInteraction() {
        super();
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef playerRef = commandBuffer == null ? null : commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        List<String> learned = new ArrayList<>();
        for (String itemId : recipeItemIds(jewelryType)) {
            if (CraftingPlugin.learnRecipe(ref, itemId, commandBuffer)) {
                learned.add(Jewelry.displayName(Jewelry.create(itemId)));
            }
        }
        if (learned.isEmpty()) {
            playerRef.sendMessage(Message.raw("You already know every " + jewelryType.toLowerCase() + " recipe."));
            context.getState().state = InteractionState.Failed;
            return;
        }
        playerRef.sendMessage(Message.raw("You can now craft: " + String.join(", ", learned)
                + ". Craft them at a workbench.").color("#e8c96a"));
    }

    /**
     * @return the ids of the jewelry items of this type that have a crafting recipe, sorted
     */
    public static List<String> recipeItemIds(String jewelryType) {
        TreeMap<String, String> ids = new TreeMap<>();
        for (JewelryDefinition definition : JewelryDefinition.getAssetMap().getAssetMap().values()) {
            if (!jewelryType.equalsIgnoreCase(definition.getType())) {
                continue;
            }
            Item item = Item.getAssetMap().getAsset(definition.getId());
            if (item != null && item.hasRecipesToGenerate()) {
                ids.put(definition.getId(), definition.getId());
            }
        }
        return List.copyOf(ids.keySet());
    }
}
