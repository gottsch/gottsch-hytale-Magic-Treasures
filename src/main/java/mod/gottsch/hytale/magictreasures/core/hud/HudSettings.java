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
package mod.gottsch.hytale.magictreasures.core.hud;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * A player's worn-jewelry HUD settings, saved with the player (registered with an id and codec,
 * as {@code GemCuttingSkill} is). For now one: whether spell cooldowns show as seconds (the
 * default) or as bars, switched from the Treasures Case page.
 *
 * @author Mark Gottschling on 11/19/2026
 */
public class HudSettings implements Component<EntityStore> {
    /** The id it is saved under. */
    public static final String ID = "MagicTreasures_HudSettings";

    public static final BuilderCodec<HudSettings> CODEC = BuilderCodec.builder(HudSettings.class,
                    HudSettings::new)
            .append(new KeyedCodec<>("CooldownBars", Codec.BOOLEAN),
                    (s, v) -> s.cooldownBars = v, s -> s.cooldownBars).add()
            .build();

    private static ComponentType<EntityStore, HudSettings> componentType;

    private boolean cooldownBars;

    public HudSettings() {
    }

    public HudSettings(boolean cooldownBars) {
        this.cooldownBars = cooldownBars;
    }

    public static ComponentType<EntityStore, HudSettings> getComponentType() {
        return componentType;
    }

    /** Set once, when the plugin registers the component. */
    public static void setComponentType(ComponentType<EntityStore, HudSettings> type) {
        componentType = type;
    }

    /**
     * @return true if the player shows cooldowns as bars, false (the default) for seconds
     */
    public static boolean cooldownBars(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        HudSettings settings = accessor.getComponent(ref, componentType);
        return settings != null && settings.cooldownBars;
    }

    public boolean isCooldownBars() {
        return cooldownBars;
    }

    @Override
    public HudSettings clone() {
        return new HudSettings(cooldownBars);
    }
}
