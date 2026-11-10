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

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * What clicking a row of the {@link ImbuePage} does: reads the piece and the material again
 * (the player may have moved them while the page was open), checks the rules again, takes
 * one of the material and writes the changed piece back. Then the page closes.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class ImbueChoice extends ChoiceInteraction {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String SOUND = "SFX_Item_Repair";
    private static final String ERROR = "#ff7070";

    private final ItemSlot pieceSlot;
    private final String pieceId;
    private final ItemSlot materialSlot;
    private final String materialId;

    public ImbueChoice(ItemSlot pieceSlot, String pieceId, ItemSlot materialSlot, String materialId) {
        this.pieceSlot = pieceSlot;
        this.pieceId = pieceId;
        this.materialSlot = materialSlot;
        this.materialId = materialId;
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        ItemStack piece = pieceSlot.get();
        ItemStack material = materialSlot.get();
        if (piece == null || material == null || !pieceId.equals(piece.getItemId())
                || !materialId.equals(material.getItemId())) {
            playerRef.sendMessage(Message.raw("Those items moved. Open it again.").color(ERROR));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        Imbuing.Outcome outcome = Imbuing.check(piece, material);
        if (outcome == null || !outcome.allowed()) {
            // a refused row: say why, and leave the page open
            playerRef.sendMessage(Message.raw(outcome == null ? "That can't be used here." : outcome.message())
                    .color(ERROR));
            return;
        }
        if (!materialSlot.takeOne()) {
            playerRef.sendMessage(Message.raw("Couldn't use that item.").color(ERROR));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        if (!pieceSlot.set(outcome.result())) {
            // give the material back where it came from
            ItemStack back = material.withQuantity(1);
            ItemStack now = materialSlot.get();
            if (ItemStack.isEmpty(now)) {
                materialSlot.set(back);
            } else {
                materialSlot.set(now.withQuantity(now.getQuantity() + 1));
            }
            playerRef.sendMessage(Message.raw("Couldn't change the jewelry.").color(ERROR));
            player.getPageManager().setPage(ref, store, Page.None);
            return;
        }
        LOGGER.atInfo().log("%s: %s on %s -> %s", playerRef.getUsername(), materialId, pieceId,
                outcome.result().getItemId());
        playerRef.sendMessage(Message.raw(outcome.message()).color("#e8c96a"));
        player.getPageManager().setPage(ref, store, Page.None);
        SoundUtil.playSoundEvent2dToPlayer(playerRef, SOUND, SoundCategory.UI);
    }
}
