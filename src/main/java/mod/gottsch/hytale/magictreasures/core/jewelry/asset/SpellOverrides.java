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
package mod.gottsch.hytale.magictreasures.core.jewelry.asset;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * A named piece's own numbers for the spells it casts, replacing the spell's base values
 * ({@code Cost}, {@code Cooldown}, {@code Effect}, {@code Duration}). A value left out keeps
 * the spell's. The piece's factors still scale the result, so a {@code Cost} of 0 stays 0.
 * Read from JSON as a {@code "SpellOverrides": { ... }} object on a jewelry definition.
 *
 * @author Mark Gottschling on 11/13/2026
 */
public class SpellOverrides {
    public static final BuilderCodec<SpellOverrides> CODEC = BuilderCodec.builder(SpellOverrides.class, SpellOverrides::new)
            .append(new KeyedCodec<>("Cost", Codec.DOUBLE), (o, v) -> o.cost = v, o -> o.cost).add()
            .append(new KeyedCodec<>("Cooldown", Codec.DOUBLE), (o, v) -> o.cooldown = v, o -> o.cooldown).add()
            .append(new KeyedCodec<>("Effect", Codec.DOUBLE), (o, v) -> o.effect = v, o -> o.effect).add()
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (o, v) -> o.duration = v, o -> o.duration).add()
            .build();

    protected Double cost;
    protected Double cooldown;
    protected Double effect;
    protected Double duration;

    public SpellOverrides() {
    }

    @Nullable
    public Double getCost() {
        return cost;
    }

    @Nullable
    public Double getCooldown() {
        return cooldown;
    }

    @Nullable
    public Double getEffect() {
        return effect;
    }

    @Nullable
    public Double getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return "SpellOverrides{cost=" + cost + ", cooldown=" + cooldown + ", effect=" + effect
                + ", duration=" + duration + "}";
    }
}
