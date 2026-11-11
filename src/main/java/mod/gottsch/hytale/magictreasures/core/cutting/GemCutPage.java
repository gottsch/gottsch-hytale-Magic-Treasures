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
package mod.gottsch.hytale.magictreasures.core.cutting;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.imbue.ImbuePage;
import mod.gottsch.hytale.magictreasures.core.imbue.ItemSlot;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * The gem cutter's kit page: one row per stack of raw gems the player carries, with the
 * chance of a good cut at their level (or the level the gem needs). Clicking a row cuts one
 * gem ({@link GemCutChoice}); the page then opens again, so several can be cut in a row. The
 * subtitle shows the player's cutting level and XP, and the kit's uses left.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class GemCutPage extends ChoiceBasePage {
    /** The page layout, under {@code Common/UI/Custom/}. */
    public static final String LAYOUT = "Pages/MagicTreasures_GemCutPage.ui";

    private final String subtitle;
    private final boolean empty;

    private GemCutPage(PlayerRef playerRef, ChoiceElement[] elements, String subtitle) {
        super(playerRef, elements, LAYOUT);
        this.subtitle = subtitle;
        this.empty = elements.length == 0;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        if (empty) {
            commandBuilder.append(getPageLayout());
            commandBuilder.clear("#ElementList");
            commandBuilder.appendInline("#ElementList",
                    "Label { Text: \"You carry no raw gems.\"; Style: (Alignment: Center); }");
        } else {
            super.build(ref, commandBuilder, eventBuilder, store);
        }
        commandBuilder.set("#Subtitle.Text", subtitle);
    }

    /**
     * The page for a held kit: every stack of raw gems the player carries.
     */
    public static GemCutPage of(PlayerRef playerRef, ComponentAccessor<EntityStore> accessor,
                                Ref<EntityStore> ref, ItemSlot kitSlot) {
        int xp = skill(accessor, ref);
        int level = GemCutting.level(xp);
        List<GemCutElement> rows = new ArrayList<>();
        for (ItemSlot slot : ImbuePage.sectionSlots(accessor, ref)) {
            ItemStack stack = slot.get();
            Gemstone gem = GemCut.gemOfRaw(stack.getItemId());
            if (gem != null) {
                rows.add(new GemCutElement(stack, gem, level, new GemCutChoice(kitSlot, slot, stack.getItemId())));
            }
        }
        // the gems you can cut first
        rows.sort((a, b) -> Boolean.compare(!a.canCut(), !b.canCut()));
        String next = level >= GemCutting.MAX_LEVEL ? "max level"
                : xp + " / " + GemCutting.xpFor(level + 1) + " XP";
        return new GemCutPage(playerRef, rows.toArray(ChoiceElement[]::new),
                "Gem cutting level " + level + " (" + next + "). " + usesLeft(kitSlot.get()) + ".");
    }

    /**
     * @return the player's cutting XP (0 if they have never cut a gem)
     */
    public static int skill(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        GemCuttingSkill skill = accessor.getComponent(ref, GemCuttingSkill.getComponentType());
        return skill == null ? 0 : skill.getXp();
    }

    static String usesLeft(ItemStack kit) {
        if (kit == null || kit.getMaxDurability() <= 0) {
            return "Kit: unlimited uses";
        }
        return "Kit: " + (int) Math.ceil(kit.getDurability()) + " uses left";
    }
}
