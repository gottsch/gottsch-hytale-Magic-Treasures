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

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.imbue.ItemSlot;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryDefinition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Jewelry Case page (Nov 11), opened by right-clicking the case in place of the vanilla
 * container window, so each slot can show what goes in it.
 * <ul>
 *     <li>The top row is the case: slots 0 and 1 take rings, slot 2 a necklace and slot 3 a
 *     bracelet, each with a picture of its type. Clicking a piece takes it off, into the
 *     player's storage or hotbar.</li>
 *     <li>Below are the pieces the player carries (hotbar, storage, backpack). Clicking one puts
 *     it on: into the first free slot of its type, or swapped with the piece in that type's
 *     first slot.</li>
 * </ul>
 * Moving is by click, because a page's item grids neither take drags nor send clicks (tested
 * Nov 11): each slot is a one-slot grid with an invisible button over it. A page also hides the
 * player's inventory, which is why it lists their jewelry itself. The slots show plain copies
 * of the pieces (the client can't read a stack's metadata in a page slot); each slot's button
 * carries the piece's name and coloured stats as its tooltip.
 * <p>
 * Everything is read again on each click, as the player's inventory can change while the page
 * is open. Writing to the case raises an inventory change, which updates what is worn.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class JewelryCasePage extends InteractiveCustomUIPage<JewelryCasePage.Click> {
    public static final String LAYOUT = "Pages/MagicTreasures_JewelryCasePage.ui";
    /** The case's slots by jewelry type. */
    public static final String[] SLOT_TYPES = {"Ring", "Ring", "Necklace", "Bracelet"};
    /** How many carried pieces the page shows (two rows of nine, in the layout). */
    public static final int CARRIED_SLOTS = 18;
    /** Where taken-off pieces go, in order. */
    private static final int[] TAKE_OFF_SECTIONS = {InventoryComponent.STORAGE_SECTION_ID,
            InventoryComponent.HOTBAR_SECTION_ID, InventoryComponent.BACKPACK_SECTION_ID};
    /** Where carried pieces are listed from, in order. */
    private static final int[] CARRIED_SECTIONS = {InventoryComponent.HOTBAR_SECTION_ID,
            InventoryComponent.STORAGE_SECTION_ID, InventoryComponent.BACKPACK_SECTION_ID};

    private static final String NAME = "#e8c96a";
    private static final String EMPTY = "#8f9bab";
    private static final String WORN = "Worn";
    private static final String CARRIED = "Carried";

    /** The case: its section and slot. */
    private final ItemContainer caseSection;
    private final short caseSlot;
    /** The carried pieces as last shown, so a click on slot i finds the right piece. */
    private List<ItemSlot> carried = List.of();

    public JewelryCasePage(PlayerRef playerRef, ItemContainer caseSection, short caseSlot) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Click.CODEC);
        this.caseSection = caseSection;
        this.caseSlot = caseSlot;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(LAYOUT);
        fill(ref, store, commands, "");
        for (int i = 0; i < SLOT_TYPES.length; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#WornButton" + i,
                    new EventData().append(Click.ACTION, WORN).append(Click.INDEX, String.valueOf(i)));
        }
        for (int i = 0; i < CARRIED_SLOTS; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#CarryButton" + i,
                    new EventData().append(Click.ACTION, CARRIED).append(Click.INDEX, String.valueOf(i)));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Click click) {
        String message = "";
        int index = click.index();
        if (WORN.equals(click.action) && index >= 0 && index < SLOT_TYPES.length) {
            message = takeOff(ref, store, (short) index);
        } else if (CARRIED.equals(click.action) && index >= 0 && index < carried.size()) {
            message = putOn(carried.get(index));
        }
        // always answer: a click locks the page ("Loading...") until the server replies
        UICommandBuilder commands = new UICommandBuilder();
        fill(ref, store, commands, message);
        sendUpdate(commands, false);
    }

    /**
     * Moves pieces that sit in another type's slot (put there through the old vanilla window)
     * into a free slot of their own type, where there is one.
     */
    public void tidy() {
        if (caseItems() == null) {
            return;
        }
        for (short i = 0; i < SLOT_TYPES.length; i++) {
            ItemSlot from = caseSlot(i);
            JewelryDefinition definition = Jewelry.definition(from.get());
            if (definition == null || SLOT_TYPES[i].equalsIgnoreCase(definition.getType())) {
                continue;
            }
            for (short j = 0; j < SLOT_TYPES.length; j++) {
                ItemSlot into = caseSlot(j);
                ItemStack there = into.get();
                if (SLOT_TYPES[j].equalsIgnoreCase(definition.getType()) && (there == null || there.isEmpty())) {
                    ItemStack piece = from.get();
                    if (into.set(piece) && !from.clear()) {
                        into.clear();
                    }
                    break;
                }
            }
        }
    }

    /**
     * Puts a carried piece in the case: the first free slot of its type, or swapped with the
     * piece in that type's first slot.
     *
     * @return a message for the page, empty if all went well
     */
    private String putOn(ItemSlot from) {
        ItemStack piece = from.get();
        JewelryDefinition definition = Jewelry.definition(piece);
        if (definition == null) {
            return "That piece has moved.";
        }
        if (caseItems() == null) {
            return "The case has moved. Close this and open it again.";
        }
        short target = -1;
        for (short i = 0; i < SLOT_TYPES.length; i++) {
            if (SLOT_TYPES[i].equalsIgnoreCase(definition.getType())) {
                ItemStack there = caseSlot(i).get();
                if (there == null || there.isEmpty()) {
                    target = i;
                    break;
                }
                if (target < 0) {
                    target = i;
                }
            }
        }
        if (target < 0) {
            return "The case has no slot for a " + definition.getType().toLowerCase() + ".";
        }
        ItemSlot into = caseSlot(target);
        ItemStack swapped = into.get();
        // case first: if that fails, nothing has moved
        if (!into.set(piece)) {
            return "Couldn't put it in the case.";
        }
        boolean back = swapped == null || swapped.isEmpty() ? from.clear() : from.set(swapped);
        if (!back) {
            // undo, so nothing is lost or doubled
            if (swapped == null || swapped.isEmpty()) {
                into.clear();
            } else {
                into.set(swapped);
            }
            return "Couldn't move it.";
        }
        return "";
    }

    /**
     * Takes the piece in a case slot off, into the first empty slot of storage, the hotbar or
     * the backpack.
     *
     * @return a message for the page, empty if all went well
     */
    private String takeOff(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, short index) {
        ItemSlot from = caseSlot(index);
        ItemStack piece = from.get();
        if (piece == null || piece.isEmpty()) {
            return "";
        }
        ItemSlot into = emptySlot(ref, accessor);
        if (into == null) {
            return "No room in your inventory.";
        }
        if (!into.set(piece)) {
            return "Couldn't take it off.";
        }
        if (!from.clear()) {
            into.clear();
            return "Couldn't take it off.";
        }
        return "";
    }

    /**
     * Fills every slot of the page from the case and the player's inventory.
     */
    private void fill(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, UICommandBuilder commands,
                      String message) {
        ItemContainer caseItems = caseItems();
        for (short i = 0; i < SLOT_TYPES.length; i++) {
            ItemStack stack = caseItems == null || i >= caseItems.getCapacity() ? null : caseItems.getItemStack(i);
            commands.set("#WornGrid" + i + ".Slots", List.of(slot(stack)));
            tooltip(commands, "#WornButton" + i, stack, SLOT_TYPES[i] + " slot");
        }
        carried = carried(ref, accessor);
        for (int i = 0; i < CARRIED_SLOTS; i++) {
            ItemStack stack = i < carried.size() ? carried.get(i).get() : null;
            commands.set("#CarryGrid" + i + ".Slots", List.of(slot(stack)));
            tooltip(commands, "#CarryButton" + i, stack, null);
        }
        if (message.isEmpty() && carried.size() > CARRIED_SLOTS) {
            message = (carried.size() - CARRIED_SLOTS) + " more pieces aren't shown.";
        }
        if (message.isEmpty() && carried.isEmpty()) {
            message = "You carry no other jewelry.";
        }
        commands.set("#Message.Text", message);
    }

    /**
     * A page slot for a piece: a plain copy, as the client can't read a stack's metadata in a
     * page slot (it disconnects).
     */
    private static ItemGridSlot slot(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new ItemGridSlot();
        }
        return new ItemGridSlot(new ItemStack(stack.getItemId(), stack.getQuantity()));
    }

    /**
     * Sets a slot's tooltip: the piece's name and its tooltip lines, in their colours. It goes
     * on the slot's button, which covers the grid and so hides the grid's own tooltip (Nov 11).
     *
     * @param empty the tooltip for an empty slot, or null for none
     */
    private static void tooltip(UICommandBuilder commands, String button, @Nullable ItemStack stack,
                                @Nullable String empty) {
        String selector = button + ".TooltipTextSpans";
        if (Jewelry.isJewelry(stack)) {
            Message text = Message.empty().insertAll(
                    Message.raw(Jewelry.displayName(stack)).bold(true).color(NAME),
                    Message.raw("\n"),
                    Jewelry.describe(Jewelry.stats(stack), Jewelry.state(stack)));
            commands.set(selector, text);
        } else if (empty != null) {
            commands.set(selector, Message.raw(empty).color(EMPTY));
        } else {
            // not setNull: the client disconnects on a null span list (Nov 11)
            commands.set(selector, Message.raw(""));
        }
    }

    /**
     * @return the jewelry the player carries outside the case, in inventory order
     */
    private List<ItemSlot> carried(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        List<ItemSlot> out = new ArrayList<>();
        for (int sectionId : CARRIED_SECTIONS) {
            ItemContainer section = InventoryUtils.getSectionById(ref, sectionId, accessor);
            if (section == null) {
                continue;
            }
            for (short i = 0; i < section.getCapacity(); i++) {
                if (Jewelry.isJewelry(section.getItemStack(i))) {
                    out.add(ItemSlot.of(section, i));
                }
            }
        }
        return out;
    }

    @Nullable
    private ItemSlot emptySlot(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        for (int sectionId : TAKE_OFF_SECTIONS) {
            ItemContainer section = InventoryUtils.getSectionById(ref, sectionId, accessor);
            if (section == null) {
                continue;
            }
            for (short i = 0; i < section.getCapacity(); i++) {
                ItemStack stack = section.getItemStack(i);
                if (stack == null || stack.isEmpty()) {
                    return ItemSlot.of(section, i);
                }
            }
        }
        return null;
    }

    private ItemSlot caseSlot(short index) {
        return new ItemSlot(caseSection, caseSlot, index);
    }

    /**
     * @return the case's contents now, or null if the case is no longer in its slot
     */
    @Nullable
    private ItemContainer caseItems() {
        ItemStack caseStack = caseSection.getItemStack(caseSlot);
        if (caseStack == null || !WornJewelry.CASE_ITEM_ID.equals(caseStack.getItemId())) {
            return null;
        }
        return ItemStackItemContainer.getContainer(caseSection, caseSlot);
    }

    /**
     * A click on the page: which row ({@code Worn} or {@code Carried}) and which slot.
     *
     * @author Mark Gottschling on 11/11/2026
     */
    public static class Click {
        static final String ACTION = "Action";
        static final String INDEX = "Index";
        public static final BuilderCodec<Click> CODEC = BuilderCodec.builder(Click.class, Click::new)
                .append(new KeyedCodec<>(ACTION, Codec.STRING),
                        (click, value, extraInfo) -> click.action = value, (click, extraInfo) -> click.action)
                .add()
                .append(new KeyedCodec<>(INDEX, Codec.STRING),
                        (click, value, extraInfo) -> click.index = value, (click, extraInfo) -> click.index)
                .add()
                .build();

        private String action;
        private String index;

        int index() {
            try {
                return Integer.parseInt(index);
            } catch (RuntimeException e) {
                return -1;
            }
        }
    }
}
