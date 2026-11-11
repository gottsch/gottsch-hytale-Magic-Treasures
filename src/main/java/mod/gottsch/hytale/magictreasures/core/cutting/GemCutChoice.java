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

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.imbue.ItemSlot;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * What clicking a row of the {@link GemCutPage} does: checks the kit and the gems are still
 * where they were and the player's level is high enough, rolls the cut, takes one raw gem,
 * gives the cut gem (or nothing, if it shattered), adds the XP and wears the kit. Then the
 * page opens again, while the kit lasts.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class GemCutChoice extends ChoiceInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String SOUND = "SFX_Item_Repair";
    private static final String SHATTER_SOUND = "SFX_Crystal_Break";
    private static final String ERROR = "#ff7070";
    private static final String GOOD = "#e8c96a";

    private final ItemSlot kitSlot;
    private final ItemSlot gemSlot;
    private final String gemItemId;

    public GemCutChoice(ItemSlot kitSlot, ItemSlot gemSlot, String gemItemId) {
        this.kitSlot = kitSlot;
        this.gemSlot = gemSlot;
        this.gemItemId = gemItemId;
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        ItemStack kit = kitSlot.get();
        ItemStack raw = gemSlot.get();
        Gemstone gem = GemCut.gemOfRaw(gemItemId);
        if (kit == null || !GemCutPageSupplier.KIT_ITEM_ID.equals(kit.getItemId()) || raw == null
                || !gemItemId.equals(raw.getItemId()) || gem == null) {
            playerRef.sendMessage(Message.raw("Those items moved. Open the kit again.").color(ERROR));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        int xp = GemCutPage.skill(store, ref);
        int level = GemCutting.level(xp);
        String name = gem.getId().toLowerCase();
        if (level < GemCutting.minLevel(gem)) {
            // a refused row: say why, and leave the page open
            playerRef.sendMessage(Message.raw("Cutting a " + name + " needs gem cutting level "
                    + GemCutting.minLevel(gem) + "; you are level " + level + ".").color(ERROR));
            return;
        }
        GemCutting.Result result = GemCutting.cut(level, gem);
        if (!gemSlot.takeOne()) {
            playerRef.sendMessage(Message.raw("Couldn't take the gem.").color(ERROR));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if (!result.shattered()) {
            ItemStack cut = new ItemStack(result.cut().itemId(gem), 1);
            if (!give(store, ref, gemSlot, cut)) {
                // no room: put the raw gem back, and nothing happened
                ItemStack now = gemSlot.get();
                gemSlot.set(ItemStack.isEmpty(now) ? raw.withQuantity(1) : now.withQuantity(now.getQuantity() + 1));
                playerRef.sendMessage(Message.raw("No room for the cut gem.").color(ERROR));
                player.getPageManager().setPage(ref, store, Page.None);
                return;
            }
        }

        int total = xp + result.xp();
        store.putComponent(ref, GemCuttingSkill.getComponentType(), new GemCuttingSkill(total));
        if (result.shattered()) {
            playerRef.sendMessage(Message.raw("The " + name + " shattered. (+" + result.xp() + " XP)").color(ERROR));
            SoundUtil.playSoundEvent2dToPlayer(playerRef, SHATTER_SOUND, SoundCategory.UI);
        } else {
            playerRef.sendMessage(Message.raw("You cut a " + result.cut().getName().toLowerCase() + " " + name
                    + ". (+" + result.xp() + " XP)").color(GOOD));
            SoundUtil.playSoundEvent2dToPlayer(playerRef, SOUND, SoundCategory.UI);
        }
        int after = GemCutting.level(total);
        if (after > level) {
            playerRef.sendMessage(Message.raw("Gem cutting level " + after + "!").color(GOOD));
        }
        LOGGER.atInfo().log("%s cut %s: %s, xp %d -> %d", playerRef.getUsername(), gemItemId,
                result.shattered() ? "shattered" : result.cut().getName(), xp, total);

        if (!wear(kit)) {
            playerRef.sendMessage(Message.raw("Your gem cutter's kit is worn out.").color(ERROR));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        player.getPageManager().openCustomPage(ref, store, GemCutPage.of(playerRef, store, ref, kitSlot));
    }

    /**
     * Uses up one of the kit's uses.
     *
     * @return false if the kit wore out (and is gone)
     */
    private boolean wear(ItemStack kit) {
        if (kit.getMaxDurability() <= 0) {
            return true;
        }
        double left = kit.getDurability() - 1;
        if (left <= 0) {
            kitSlot.takeOne();
            return false;
        }
        kitSlot.set(kit.withDurability(left));
        return true;
    }

    /**
     * Puts the cut gem where the raw one was if that slot is now empty, or else anywhere in
     * the hotbar, storage or backpack.
     */
    private static boolean give(Store<EntityStore> store, Ref<EntityStore> ref, ItemSlot where, ItemStack stack) {
        if (ItemStack.isEmpty(where.get()) && where.set(stack)) {
            return true;
        }
        List<ComponentType<EntityStore, ? extends InventoryComponent>> types = List.of(
                InventoryComponent.Hotbar.getComponentType(), InventoryComponent.Storage.getComponentType(),
                InventoryComponent.Backpack.getComponentType());
        for (ComponentType<EntityStore, ? extends InventoryComponent> type : types) {
            InventoryComponent inventory = store.getComponent(ref, type);
            if (inventory != null && inventory.getInventory().canAddItemStack(stack)
                    && inventory.getInventory().addItemStack(stack).succeeded()) {
                return true;
            }
        }
        return false;
    }
}
