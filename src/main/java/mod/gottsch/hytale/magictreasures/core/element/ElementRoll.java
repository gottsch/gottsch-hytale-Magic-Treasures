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

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nullable;

/**
 * One elemental property a metal or gem may give, as written in its JSON ({@code Elements},
 * and a gem's {@code Bonus}):
 * <pre>
 * {"Element": "Fire", "Kind": "Resistance", "Chance": 0.5, "Min": 0.05, "Max": 0.12}
 * {"Element": "Fire", "Kind": "OnHit", "Effect": "Burn", "Min": 0.08, "Max": 0.10}
 * </pre>
 * {@code Element} is a vanilla damage cause ({@code Fire}, {@code Ice}, {@code Lightning},
 * {@code Earth}, {@code Water}, {@code Wind}, {@code Poison}), or {@code Elemental} for all of
 * them. {@code Kind} is {@link ElementProperty.Kind}. {@code Chance} is how likely the property
 * is to come up when a piece is made (1 if absent). The value is rolled between {@code Min}
 * and {@code Max}: a share of damage (0.1 is 10%), negative for a weakness; for an on-hit
 * property it is the chance per hit to give the target the status {@code Effect}.
 *
 * @author Mark Gottschling on 11/11/2026
 */
public class ElementRoll {
    public static final BuilderCodec<ElementRoll> CODEC = BuilderCodec.builder(ElementRoll.class, ElementRoll::new)
            .append(new KeyedCodec<>("Element", Codec.STRING), (r, v) -> r.element = v, r -> r.element).add()
            .append(new KeyedCodec<>("Kind", Codec.STRING), (r, v) -> r.kind = v, r -> r.kind).add()
            .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (r, v) -> r.chance = v, r -> r.chance).add()
            .append(new KeyedCodec<>("Min", Codec.DOUBLE), (r, v) -> r.min = v, r -> r.min).add()
            .append(new KeyedCodec<>("Max", Codec.DOUBLE), (r, v) -> r.max = v, r -> r.max).add()
            .append(new KeyedCodec<>("Effect", Codec.STRING), (r, v) -> r.effect = v, r -> r.effect).add()
            .build();

    public static final ArrayCodec<ElementRoll> ARRAY_CODEC = new ArrayCodec<>(CODEC, ElementRoll[]::new);
    public static final ElementRoll[] NONE = new ElementRoll[0];

    protected String element;
    protected String kind;
    protected double chance = 1.0;
    protected double min;
    protected double max;
    protected String effect;

    public ElementRoll() {
    }

    public String getElement() {
        return element;
    }

    /**
     * @return the kind, or null if the JSON names none we know
     */
    @Nullable
    public ElementProperty.Kind getKind() {
        return ElementProperty.Kind.of(kind);
    }

    public double getChance() {
        return chance;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return Math.max(min, max);
    }

    @Nullable
    public String getEffect() {
        return effect;
    }
}
