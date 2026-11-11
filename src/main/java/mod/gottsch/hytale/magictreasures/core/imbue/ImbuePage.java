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

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.cutting.GemCut;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The page for working on jewelry, built on vanilla's repair-kit page ({@code ChoiceBasePage}:
 * a list of buttons, one per choice). It opens two ways:
 * <ul>
 *     <li>from a <b>held spell scroll</b>: it lists your jewelry, including the pieces in a
 *     Jewelry Case, and what the scroll would do to each;</li>
 *     <li>from a <b>held piece of jewelry</b>: it lists what you carry that works on it
 *     (spell scrolls, runes, gems, crystals and other jewelry) and what each would do.</li>
 * </ul>
 * Choices that can't be used are listed too, with the reason, so it's clear why. Clicking one
 * sends the reason to chat.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class ImbuePage extends ChoiceBasePage {
    /** The page layout, under {@code Common/UI/Custom/}. */
    public static final String LAYOUT = "Pages/MagicTreasures_ImbuePage.ui";

    private final String subtitle;
    private final boolean empty;

    private ImbuePage(PlayerRef playerRef, ChoiceElement[] elements, String subtitle) {
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
                    "Label { Text: \"Nothing you carry can be used here.\"; Style: (Alignment: Center); }");
        } else {
            super.build(ref, commandBuilder, eventBuilder, store);
        }
        commandBuilder.set("#Subtitle.Text", subtitle);
    }

    /**
     * The page for a held spell scroll: every piece of jewelry the player carries.
     */
    public static ImbuePage forScroll(PlayerRef playerRef, ComponentAccessor<EntityStore> accessor,
                                      Ref<EntityStore> ref, ItemSlot scroll) {
        ItemStack scrollStack = scroll.get();
        List<ImbueElement> elements = new ArrayList<>();
        for (ItemSlot pieceSlot : pieces(accessor, ref)) {
            ItemStack piece = pieceSlot.get();
            Imbuing.Outcome outcome = Imbuing.check(piece, scrollStack);
            if (outcome != null) {
                String where = pieceSlot.inCase() ? " (in case)" : "";
                elements.add(new ImbueElement(piece.getItemId(), Message.join(Jewelry.name(piece), Message.raw(where)),
                        outcome,
                        new ImbueChoice(pieceSlot, piece.getItemId(), scroll, scrollStack.getItemId())));
            }
        }
        sort(elements);
        return new ImbuePage(playerRef, elements.toArray(ChoiceElement[]::new),
                (Imbuing.kindOf(scrollStack) == Imbuing.Kind.GEM ? "Choose the jewelry to set with a "
                        : "Choose the jewelry to imbue with ") + name(scrollStack) + ".");
    }

    /**
     * The page for a held piece of jewelry: everything the player carries that works on it.
     */
    public static ImbuePage forPiece(PlayerRef playerRef, ComponentAccessor<EntityStore> accessor,
                                     Ref<EntityStore> ref, ItemSlot pieceSlot) {
        ItemStack piece = pieceSlot.get();
        List<ImbueElement> elements = new ArrayList<>();
        for (ItemSlot materialSlot : sectionSlots(accessor, ref)) {
            if (materialSlot.sameSlot(pieceSlot)) {
                continue;
            }
            ItemStack material = materialSlot.get();
            Imbuing.Outcome outcome = Imbuing.check(piece, material);
            if (outcome != null) {
                Message label = Jewelry.isJewelry(material) ? Jewelry.name(material) : Message.raw(name(material));
                elements.add(new ImbueElement(material.getItemId(), label, outcome,
                        new ImbueChoice(pieceSlot, piece.getItemId(), materialSlot, material.getItemId())));
            }
        }
        sort(elements);
        return new ImbuePage(playerRef, elements.toArray(ChoiceElement[]::new),
                "Choose what to use on your " + Jewelry.displayName(piece) + ".");
    }

    /** Usable choices first, then by kind, keeping the inventory order otherwise. */
    private static void sort(List<ImbueElement> elements) {
        elements.sort(Comparator.comparing((ImbueElement e) -> !e.outcome().allowed())
                .thenComparing(e -> e.outcome().kind()));
    }

    /**
     * @return every piece of jewelry the player carries: in the hotbar, utility, storage and
     * backpack, and inside any Jewelry Case there
     */
    static List<ItemSlot> pieces(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        List<ItemSlot> found = new ArrayList<>();
        for (ItemSlot slot : sectionSlots(accessor, ref)) {
            ItemStack stack = slot.get();
            if (Jewelry.isJewelry(stack)) {
                found.add(slot);
            } else if (stack != null && WornJewelry.CASE_ITEM_ID.equals(stack.getItemId())) {
                ItemStack[] contents = WornJewelry.contents(stack);
                for (short i = 0; i < contents.length; i++) {
                    if (Jewelry.isJewelry(contents[i])) {
                        found.add(new ItemSlot(slot.container(), slot.slot(), i));
                    }
                }
            }
        }
        return found;
    }

    /**
     * @return every non-empty slot of the hotbar, utility, storage and backpack
     */
    public static List<ItemSlot> sectionSlots(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        List<ItemSlot> slots = new ArrayList<>();
        add(slots, accessor, ref, InventoryComponent.Hotbar.getComponentType());
        add(slots, accessor, ref, InventoryComponent.Utility.getComponentType());
        add(slots, accessor, ref, InventoryComponent.Storage.getComponentType());
        add(slots, accessor, ref, InventoryComponent.Backpack.getComponentType());
        return slots;
    }

    private static <T extends InventoryComponent> void add(List<ItemSlot> slots, ComponentAccessor<EntityStore> accessor,
                                                           Ref<EntityStore> ref, ComponentType<EntityStore, T> type) {
        T component = accessor.getComponent(ref, type);
        if (component == null) {
            return;
        }
        ItemContainer container = component.getInventory();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (!ItemStack.isEmpty(container.getItemStack(slot))) {
                slots.add(ItemSlot.of(container, slot));
            }
        }
    }

    /**
     * @return a plain name for a non-jewelry item, from its id ({@code Rune_Fireball} becomes
     * {@code Fireball}, {@code MT_Scroll_Greater_Healing} becomes {@code Greater Healing})
     */
    static String name(ItemStack stack) {
        String id = stack.getItemId();
        GemCut cut = GemCut.ofItem(id);
        if (cut != null) {
            Gemstone gem = GemCut.gemOfItem(id);
            return cut.getName() + " " + (gem == null ? id : gem.getId());
        }
        if (id.startsWith(GemCut.RAW_PREFIX)) {
            return "Raw " + id.substring(GemCut.RAW_PREFIX.length());
        }
        if (id.startsWith(Imbuing.SCROLL_PREFIX)) {
            id = id.substring(Imbuing.SCROLL_PREFIX.length());
        }
        return id.replace("Rune_", "").replace("Rock_Gem_", "").replace("Ingredient_Crystal_", "Crystal ")
                .replace('_', ' ');
    }
}
