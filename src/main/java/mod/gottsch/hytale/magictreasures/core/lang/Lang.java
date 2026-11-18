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
package mod.gottsch.hytale.magictreasures.core.lang;

import com.hypixel.hytale.server.core.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Text players read, as translation keys in {@code server.lang} (v1.2, for the pt-BR
 * translation). The client translates a key into the player's own language, so a tooltip
 * saved on an item reads right for everyone who sees it.
 * <p>
 * The mod's own keys are {@code magictreasures.*} in the lang file ({@code server.} is added
 * here, as for every key in {@code server.lang}). Parameters are written {@code {name}};
 * numbers are passed already formatted, as text.
 * <p>
 * Operator and debug commands ({@code info}, {@code hits}, {@code loot}, {@code give},
 * {@code setcutting}) stay in English.
 *
 * @author Mark Gottschling on 11/18/2026
 */
public final class Lang {
    public static final String PREFIX = "server.magictreasures.";

    private Lang() {
    }

    /** @return the mod's text with this key, {@code magictreasures.<key>} in the lang file */
    public static Message of(String key) {
        return Message.translation(PREFIX + key);
    }

    /** @return a spell's name ({@code magictreasures.spells.<id>.name}, generated) */
    public static Message spell(String spellId) {
        return Message.translation(PREFIX + "spells." + spellId + ".name");
    }

    /**
     * @return an element (a damage cause id: {@code Fire}, {@code Elemental}, ...) as it
     * starts a line: "Fire", "Fogo"
     */
    public static Message element(String element) {
        return Message.translation(PREFIX + "elements." + element);
    }

    /** @return an element inside a sentence: "fire", "fogo" */
    public static Message elementLower(String element) {
        return Message.translation(PREFIX + "elements." + element + ".lower");
    }

    /**
     * @return a status given on hit, from its effect id ({@code Burn},
     * {@code MT_Status_Stun})
     */
    public static Message status(String effectId) {
        return Message.translation(PREFIX + "status." + effectId.replaceFirst("^MT_Status_", ""));
    }

    /**
     * @return a metal or gem as a power's source, inside a sentence: "silver", "prata"
     */
    public static Message source(String id) {
        return Message.translation(PREFIX + "sources." + id);
    }

    /** @return an item's own name, {@code items.<id>.name} (a rune, a cut gem, a piece) */
    public static Message item(String itemId) {
        return Message.translation("server.items." + itemId + ".name");
    }

    /** @return the parts one after another, with the separator between them */
    public static Message join(List<Message> parts, String separator) {
        List<Message> out = new ArrayList<>();
        for (Message part : parts) {
            if (!out.isEmpty()) {
                out.add(Message.raw(separator));
            }
            out.add(part);
        }
        return Message.empty().insertAll(out);
    }
}
