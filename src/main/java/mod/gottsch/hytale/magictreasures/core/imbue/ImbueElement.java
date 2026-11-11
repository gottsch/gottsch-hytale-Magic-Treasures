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

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * One row of the {@link ImbuePage}: an item's icon and name, and what using it would do (or
 * why it can't be used). It reuses vanilla's repair-kit row layout, whose right-hand label
 * normally shows durability.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class ImbueElement extends ChoiceElement {
    private static final String ROW_LAYOUT = "Pages/ItemRepairElement.ui";

    private final String iconItemId;
    private final Message label;
    private final Imbuing.Outcome outcome;

    public ImbueElement(String iconItemId, Message label, Imbuing.Outcome outcome, ImbueChoice choice) {
        this.iconItemId = iconItemId;
        this.label = label;
        this.outcome = outcome;
        this.interactions = new ChoiceInteraction[]{choice};
    }

    public Imbuing.Outcome outcome() {
        return outcome;
    }

    @Override
    public void addButton(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector,
                          PlayerRef playerRef) {
        commandBuilder.append("#ElementList", ROW_LAYOUT);
        commandBuilder.set(selector + " #Icon.ItemId", iconItemId);
        commandBuilder.set(selector + " #Name.TextSpans",
                label.color(outcome.allowed() ? "#ffffff" : "#8a8a8a"));
        commandBuilder.set(selector + " #Durability.Text", outcome.text());
    }
}
