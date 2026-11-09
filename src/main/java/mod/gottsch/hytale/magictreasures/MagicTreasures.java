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
package mod.gottsch.hytale.magictreasures;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.command.MagicTreasuresCommand;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.JewelryAssets;
import mod.gottsch.hytale.magictreasures.core.spell.DrainDamageSystem;
import mod.gottsch.hytale.magictreasures.core.spell.HitMeter;
import mod.gottsch.hytale.magictreasures.core.spell.SpellEngine;
import mod.gottsch.hytale.magictreasures.core.spell.SpellTickSystem;
import mod.gottsch.hytale.magictreasures.core.wear.JewelryWear;
import mod.gottsch.hytale.magictreasures.core.wear.WornJewelrySystem;

import javax.annotation.Nonnull;

/**
 * Magic Treasures plugin entry point: registers the jewelry asset types, the commands, and
 * the worn-jewelry tracking (an inventory system plus join and leave events).
 *
 * @author Mark Gottschling on 11/08/2026
 */
public class MagicTreasures extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public MagicTreasures(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up %s version %s", this.getName(), this.getManifest().getVersion().toString());
        JewelryAssets.register(this.getAssetRegistry());
        this.getCommandRegistry().registerCommand(new MagicTreasuresCommand());
        // worn jewelry: re-read on every inventory change, and shown again after joining
        this.getEntityStoreRegistry().registerSystem(new WornJewelrySystem());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, MagicTreasures::onReady);
        // passive spells: tick spells once a second, hit spells (Drain) on damage dealt
        this.getEntityStoreRegistry().registerSystem(new SpellTickSystem());
        this.getEntityStoreRegistry().registerSystem(new DrainDamageSystem());
        this.getEntityStoreRegistry().registerSystem(new HitMeter.Before());
        this.getEntityStoreRegistry().registerSystem(new HitMeter.After());
        this.getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            JewelryWear.onLeave(event.getPlayerRef().getUuid());
            SpellEngine.onLeave(event.getPlayerRef().getUuid());
        });
    }

    private static void onReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> {
            if (ref.isValid()) {
                JewelryWear.onJoin(ref, store);
            }
        });
    }
}
