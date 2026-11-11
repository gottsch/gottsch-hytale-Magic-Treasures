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
package mod.gottsch.hytale.magictreasures.core.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import mod.gottsch.hytale.magictreasures.core.element.ElementProperty;
import mod.gottsch.hytale.magictreasures.core.element.ElementRolling;
import mod.gottsch.hytale.magictreasures.core.jewelry.Jewelry;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryState;
import mod.gottsch.hytale.magictreasures.core.jewelry.JewelryStats;
import mod.gottsch.hytale.magictreasures.core.spell.asset.Spell;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * /mt info: prints the held piece of jewelry's definition, computed stats and saved state.
 * <p>
 * For testing, {@code --mana=N} sets the piece's mana, {@code --uses=N} its uses,
 * {@code --rune=ID} adds a core rune (up to the piece's sockets), {@code --spell=ID} adds a
 * spell (up to the piece's max level), and {@code --clearspells=true} and
 * {@code --clearrunes=true} remove them. The stack is then written back to the hotbar slot,
 * which rewrites its tooltip.
 *
 * @author Mark Gottschling on 11/09/2026
 */
public class InfoCommand extends AbstractPlayerCommand {
    private final OptionalArg<Double> manaArg;
    private final OptionalArg<String> runeArg;
    private final OptionalArg<String> spellArg;
    private final OptionalArg<Integer> usesArg;
    private final DefaultArg<Boolean> clearSpellsArg;
    private final DefaultArg<Boolean> clearRunesArg;
    private final DefaultArg<Boolean> rerollArg;

    public InfoCommand() {
        super("info", "Show the held jewelry's stats and state.");
        this.manaArg = withOptionalArg("mana", "Test: set the held piece's mana", ArgTypes.DOUBLE);
        this.runeArg = withOptionalArg("rune", "Test: add a rune id to the held piece", ArgTypes.STRING);
        this.spellArg = withOptionalArg("spell", "Test: add a spell id to the held piece", ArgTypes.STRING);
        this.usesArg = withOptionalArg("uses", "Test: set the held piece's uses", ArgTypes.INTEGER);
        this.clearSpellsArg = withDefaultArg("clearspells", "Test: remove the held piece's spells", ArgTypes.BOOLEAN,
                false, "false");
        this.clearRunesArg = withDefaultArg("clearrunes", "Test: remove the held piece's runes", ArgTypes.BOOLEAN,
                false, "false");
        this.rerollArg = withDefaultArg("reroll", "Test: roll the held piece's elements again", ArgTypes.BOOLEAN,
                false, "false");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            context.sendMessage(Message.raw("You have no hotbar."));
            return;
        }
        byte slot = hotbar.getActiveSlot();
        ItemStack stack = hotbar.getActiveItem();
        if (!Jewelry.isJewelry(stack)) {
            context.sendMessage(Message.raw("Hold a piece of Magic Treasures jewelry. Holding: "
                    + (stack == null || stack.isEmpty() ? "nothing" : stack.getItemId())));
            return;
        }

        JewelryStats stats = Jewelry.stats(stack);
        JewelryState state = Jewelry.state(stack);
        boolean saved = stack.getFromMetadataOrNull(JewelryState.KEYED_CODEC) != null;

        if (manaArg.provided(context) || runeArg.provided(context) || spellArg.provided(context)
                || usesArg.provided(context) || clearSpellsArg.get(context) || clearRunesArg.get(context)
                || rerollArg.get(context)) {
            state = state.copy();
            if (rerollArg.get(context)) {
                // withState rolls metal and gem again
                state.setElements().setRolled(false);
            }
            if (manaArg.provided(context)) {
                state.setMana(manaArg.get(context));
            }
            if (usesArg.provided(context)) {
                state.setUses(usesArg.get(context));
            }
            if (clearSpellsArg.get(context)) {
                state.setSpells();
            }
            if (clearRunesArg.get(context)) {
                state.setRunes();
            }
            if (spellArg.provided(context)) {
                Spell spell = Spell.get(spellArg.get(context));
                if (spell == null) {
                    context.sendMessage(Message.raw("No spell " + spellArg.get(context) + ". Spells: "
                            + String.join(", ", new TreeSet<>(Spell.getAssetMap().getAssetMap().keySet()))));
                    return;
                }
                if (spell.getLevel() > stats.maxLevel()) {
                    context.sendMessage(Message.raw(spell.getName() + " is level " + spell.getLevel()
                            + "; this piece holds spells up to level " + stats.maxLevel() + "."));
                    return;
                }
                String[] spells = Arrays.copyOf(state.getSpells(), state.getSpells().length + 1);
                spells[spells.length - 1] = spell.getId();
                state.setSpells(spells);
            }
            if (runeArg.provided(context)) {
                Item runeItem = Item.getAssetMap().getAsset(runeArg.get(context));
                if (runeItem == null || !(runeItem.getAbility() instanceof CoreItemAbility)) {
                    context.sendMessage(Message.raw(runeArg.get(context)
                            + " isn't a core rune. Try Rune_Fireball, Rune_WindStrike or Rune_GroundSlam."));
                    return;
                }
                if (state.getRunes().length >= stats.sockets()) {
                    context.sendMessage(Message.raw("This piece's rune sockets are full ("
                            + stats.sockets() + "). Use --clearrunes=true first."));
                    return;
                }
                String[] runes = Arrays.copyOf(state.getRunes(), state.getRunes().length + 1);
                runes[runes.length - 1] = runeArg.get(context);
                state.setRunes(runes);
            }
            ItemStack updated = Jewelry.withState(stack, state);
            hotbar.getInventory().setItemStackForSlot(slot, updated);
            state = Jewelry.state(updated);
            saved = true;
            context.sendMessage(Message.raw("Updated the held piece. Hover it to check the tooltip."));
        }

        context.sendMessage(Message.raw(stack.getItemId() + ": " + stats.definition().getType()
                + ", material " + stats.definition().getMaterial()
                + ", style " + stats.definition().getStyle()
                + ", gem " + (stats.definition().getGem() == null ? "none" : stats.definition().getGem())
                + (stats.known() ? "" : " (material or style missing)")));
        context.sendMessage(Message.raw(String.format(
                "Stats: mana %.0f, uses %d, level %d, repairs %d, recharges %d, sockets %d, quality %s",
                stats.maxMana(), stats.maxUses(), stats.maxLevel(), stats.maxRepairs(), stats.maxRecharges(),
                stats.sockets(), stats.quality())));
        context.sendMessage(Message.raw("Factors: " + stats.factors()));
        context.sendMessage(Message.raw("State" + (saved ? "" : " (not saved yet, shown fresh)") + ": " + state));
        List<ElementProperty> elements = ElementRolling.decode(state);
        context.sendMessage(Message.raw("Elements" + (state.isRolled() ? "" : " (not rolled yet)") + ": "
                + (elements.isEmpty() ? "none" : String.join(", ",
                elements.stream().map(ElementProperty::describe).toList()))));
    }
}
