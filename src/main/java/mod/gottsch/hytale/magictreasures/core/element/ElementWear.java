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
package mod.gottsch.hytale.magictreasures.core.element;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player's worn jewelry adds up to elementally, for {@link ElementDamageSystem}:
 * kept per player and recomputed whenever their worn jewelry is re-read (every inventory
 * change), so a hit only adds up a short list.
 * <p>
 * Also stamps rolls onto jewelry that has none yet (crafted, or made before elements):
 * {@link #needsStamp} is read-only and safe inside a system; {@link #stamp} writes, so it
 * runs on the world thread afterwards.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public final class ElementWear {
    /** Resistance (and weakness) is held within this, either way. */
    public static final double RESISTANCE_CAP = 0.5;
    /** Crit chance is held within this. */
    public static final double CRIT_CAP = 0.5;
    /** What a critical hit's damage is multiplied by. */
    public static final double CRIT_MULTIPLIER = 1.5;

    private static final Map<UUID, List<ElementProperty>> WORN = new ConcurrentHashMap<>();

    private ElementWear() {
    }

    /**
     * Re-adds the worn pieces' properties for the player.
     */
    public static void update(UUID uuid, WornJewelry worn) {
        List<ElementProperty> all = new ArrayList<>();
        for (ItemStack piece : worn.pieces()) {
            JewelryState state = piece.getFromMetadataOrNull(JewelryState.KEYED_CODEC);
            if (state != null) {
                all.addAll(ElementRolling.decode(state));
            }
        }
        if (all.isEmpty()) {
            WORN.remove(uuid);
        } else {
            WORN.put(uuid, List.copyOf(all));
        }
    }

    public static void onLeave(UUID uuid) {
        WORN.remove(uuid);
    }

    /**
     * @return the player's worn properties, empty if none
     */
    public static List<ElementProperty> properties(UUID uuid) {
        return WORN.getOrDefault(uuid, List.of());
    }

    /**
     * @return the player's resistance to this damage, within the cap (negative: a weakness)
     */
    public static double resistance(UUID uuid, DamageCause cause) {
        double total = sum(uuid, ElementProperty.Kind.RESISTANCE, cause);
        return Math.max(-RESISTANCE_CAP, Math.min(RESISTANCE_CAP, total));
    }

    /**
     * @return the player's worn element damage, element id to the share of each weapon hit it
     * adds as that element's damage ("+10% as Fire damage" is {@code Fire -> 0.10}); only
     * positive shares (Nov 12: these were multipliers on damage already of that element, and
     * almost no weapon deals elemental damage)
     */
    public static Map<String, Double> damageShares(UUID uuid) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (ElementProperty property : properties(uuid)) {
            if (property.kind() == ElementProperty.Kind.DAMAGE && property.value() > 0) {
                out.merge(property.element(), property.value(), Double::sum);
            }
        }
        return out;
    }

    /**
     * @return the player's chance per melee or ranged hit to crit, within {@link #CRIT_CAP}
     */
    public static double critChance(UUID uuid) {
        double total = 0;
        for (ElementProperty property : properties(uuid)) {
            if (property.kind() == ElementProperty.Kind.CRIT) {
                total += property.value();
            }
        }
        return Math.max(0, Math.min(CRIT_CAP, total));
    }

    private static double sum(UUID uuid, ElementProperty.Kind kind, DamageCause cause) {
        double total = 0;
        for (ElementProperty property : properties(uuid)) {
            if (property.kind() == kind && matches(property.element(), cause)) {
                total += property.value();
            }
        }
        return total;
    }

    /**
     * @return true if damage of this cause is of the element ({@code Elemental} covers every
     * elemental cause)
     */
    public static boolean matches(String element, @Nullable DamageCause cause) {
        if (cause == null) {
            return false;
        }
        DamageCause target = DamageCause.getAssetMap().getAsset(element);
        return target != null && cause.isOrInherits(target);
    }

    /**
     * @return true if the player carries jewelry that hasn't been rolled yet
     */
    public static boolean needsStamp(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        for (ItemContainer container : sections(ref, accessor)) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (unrolled(stack)) {
                    return true;
                }
                if (stack != null && WornJewelry.CASE_ITEM_ID.equals(stack.getItemId())) {
                    for (ItemStack inner : WornJewelry.contents(stack)) {
                        if (unrolled(inner)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Rolls every unrolled piece the player carries, in the inventory and in Treasures Cases.
     * Writes the inventory, so call it on the world thread, outside the systems.
     */
    public static void stamp(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        for (ItemContainer container : sections(ref, accessor)) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (unrolled(stack)) {
                    container.setItemStackForSlot(slot, Jewelry.withState(stack, Jewelry.state(stack)));
                } else if (stack != null && WornJewelry.CASE_ITEM_ID.equals(stack.getItemId())) {
                    ItemStack[] contents = WornJewelry.contents(stack);
                    ItemStackItemContainer inner = null;
                    for (short i = 0; i < contents.length; i++) {
                        if (!unrolled(contents[i])) {
                            continue;
                        }
                        if (inner == null) {
                            inner = ItemStackItemContainer.getContainer(container, slot);
                            if (inner == null) {
                                break;
                            }
                        }
                        inner.setItemStackForSlot(i, Jewelry.withState(contents[i], Jewelry.state(contents[i])));
                    }
                }
            }
        }
    }

    private static boolean unrolled(@Nullable ItemStack stack) {
        if (!Jewelry.isJewelry(stack)) {
            return false;
        }
        JewelryState state = stack.getFromMetadataOrNull(JewelryState.KEYED_CODEC);
        return state == null || !state.isRolled();
    }

    private static List<ItemContainer> sections(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        List<ItemContainer> out = new ArrayList<>();
        add(out, accessor, ref, InventoryComponent.Utility.getComponentType());
        add(out, accessor, ref, InventoryComponent.Hotbar.getComponentType());
        add(out, accessor, ref, InventoryComponent.Storage.getComponentType());
        add(out, accessor, ref, InventoryComponent.Backpack.getComponentType());
        return out;
    }

    private static <T extends InventoryComponent> void add(List<ItemContainer> out,
                                                            ComponentAccessor<EntityStore> accessor,
                                                            Ref<EntityStore> ref, ComponentType<EntityStore, T> type) {
        T component = accessor.getComponent(ref, type);
        if (component != null) {
            out.add(component.getInventory());
        }
    }
}
