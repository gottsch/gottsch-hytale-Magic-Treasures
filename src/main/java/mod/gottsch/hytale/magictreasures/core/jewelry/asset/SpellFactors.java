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

/**
 * Multipliers a material or gem applies to the spells cast by a piece of jewelry, as in
 * Magic Treasures 2.0: {@code Cost}, {@code Cooldown}, {@code Effect}, {@code Duration} and
 * {@code Range}. Every factor defaults to 1 (no change). Read from JSON as a
 * {@code "Factors": { ... }} object.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class SpellFactors {
    public static final SpellFactors NONE = new SpellFactors();

    public static final BuilderCodec<SpellFactors> CODEC = BuilderCodec.builder(SpellFactors.class, SpellFactors::new)
            .append(new KeyedCodec<>("Cost", Codec.DOUBLE), (f, v) -> f.cost = v, f -> f.cost).add()
            .append(new KeyedCodec<>("Cooldown", Codec.DOUBLE), (f, v) -> f.cooldown = v, f -> f.cooldown).add()
            .append(new KeyedCodec<>("Effect", Codec.DOUBLE), (f, v) -> f.effect = v, f -> f.effect).add()
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (f, v) -> f.duration = v, f -> f.duration).add()
            .append(new KeyedCodec<>("Range", Codec.DOUBLE), (f, v) -> f.range = v, f -> f.range).add()
            .build();

    protected double cost = 1.0;
    protected double cooldown = 1.0;
    protected double effect = 1.0;
    protected double duration = 1.0;
    protected double range = 1.0;

    public SpellFactors() {
    }

    public SpellFactors(double cost, double cooldown, double effect, double duration, double range) {
        this.cost = cost;
        this.cooldown = cooldown;
        this.effect = effect;
        this.duration = duration;
        this.range = range;
    }

    /**
     * @return these factors multiplied by {@code other}'s, factor by factor
     */
    public SpellFactors multiply(SpellFactors other) {
        if (other == null) {
            return this;
        }
        return new SpellFactors(cost * other.cost, cooldown * other.cooldown, effect * other.effect,
                duration * other.duration, range * other.range);
    }

    public double getCost() {
        return cost;
    }

    public double getCooldown() {
        return cooldown;
    }

    public double getEffect() {
        return effect;
    }

    public double getDuration() {
        return duration;
    }

    public double getRange() {
        return range;
    }

    @Override
    public String toString() {
        return String.format("cost %.3f, cooldown %.3f, effect %.3f, duration %.3f, range %.3f",
                cost, cooldown, effect, duration, range);
    }
}
