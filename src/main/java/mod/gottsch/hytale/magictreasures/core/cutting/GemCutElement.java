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

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import mod.gottsch.hytale.magictreasures.core.jewelry.asset.Gemstone;

/**
 * One row of the {@link GemCutPage}: a stack of raw gems and the odds of cutting one, or
 * the level it needs. Reuses vanilla's repair-kit row layout.
 *
 * @author Mark Gottschling on 11/10/2026
 */
public class GemCutElement extends ChoiceElement {
    private static final String ROW_LAYOUT = "Pages/ItemRepairElement.ui";

    private final String itemId;
    private final String label;
    private final String text;
    private final boolean canCut;

    public GemCutElement(ItemStack stack, Gemstone gem, int level, GemCutChoice choice) {
        this.itemId = stack.getItemId();
        this.label = "Raw " + gem.getId().toLowerCase() + " x" + stack.getQuantity();
        this.canCut = level >= GemCutting.minLevel(gem);
        this.text = canCut ? odds(level, gem) : "Needs level " + GemCutting.minLevel(gem);
        this.interactions = new ChoiceInteraction[]{choice};
    }

    public boolean canCut() {
        return canCut;
    }

    /**
     * @return e.g. "Flawless+ 20%, shatter 8%"
     */
    static String odds(int level, Gemstone gem) {
        double[] odds = GemCutting.odds(level, gem);
        String text = String.format("Flawless+ %.0f%%", odds[2] + odds[3]);
        return odds[4] > 0 ? text + String.format(", shatter %.0f%%", odds[4]) : text;
    }

    @Override
    public void addButton(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector,
                          PlayerRef playerRef) {
        commandBuilder.append("#ElementList", ROW_LAYOUT);
        commandBuilder.set(selector + " #Icon.ItemId", itemId);
        commandBuilder.set(selector + " #Name.TextSpans", Message.raw(label).color(canCut ? "#ffffff" : "#8a8a8a"));
        commandBuilder.set(selector + " #Durability.Text", text);
    }
}
