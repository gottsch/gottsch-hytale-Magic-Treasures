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

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * A player's gem-cutting skill: the XP earned by cutting gems, saved with the player
 * (registered with an id and codec, so the entity store writes it). The level follows from
 * the XP: {@link GemCutting#level(int)}.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class GemCuttingSkill implements Component<EntityStore> {
    /** The id it is saved under. */
    public static final String ID = "MagicTreasures_GemCutting";

    public static final BuilderCodec<GemCuttingSkill> CODEC = BuilderCodec.builder(GemCuttingSkill.class,
                    GemCuttingSkill::new)
            .append(new KeyedCodec<>("Xp", Codec.INTEGER), (s, v) -> s.xp = v, s -> s.xp).add()
            .build();

    private static ComponentType<EntityStore, GemCuttingSkill> componentType;

    private int xp;

    public GemCuttingSkill() {
    }

    public GemCuttingSkill(int xp) {
        this.xp = xp;
    }

    public static ComponentType<EntityStore, GemCuttingSkill> getComponentType() {
        return componentType;
    }

    /** Set once, when the plugin registers the component. */
    public static void setComponentType(ComponentType<EntityStore, GemCuttingSkill> type) {
        componentType = type;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = Math.max(0, xp);
    }

    public int getLevel() {
        return GemCutting.level(xp);
    }

    @Override
    public GemCuttingSkill clone() {
        return new GemCuttingSkill(xp);
    }
}
