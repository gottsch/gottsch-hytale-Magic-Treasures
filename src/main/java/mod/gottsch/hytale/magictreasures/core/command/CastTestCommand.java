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
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /mt cast - feasibility test: casts a vanilla rune's ability (its root interaction, for example
 * {@code Root_Ability_Fireball}) on the calling player from code, without the player holding or
 * slotting the rune. This is what worn jewelry casting a spell would have to do.
 * <p>
 * Two ways to start the chain, picked with {@code --mode}:
 * <ul>
 *     <li>{@code queue} (default): {@link InteractionManager#initChain} then
 *     {@link InteractionManager#queueExecuteChain}, so the manager runs it on its next tick.</li>
 *     <li>{@code try}: {@link InteractionManager#tryStartChain} with a command buffer for the player's chunk.</li>
 * </ul>
 * It reports the player's mana before the cast, so a test shows whether the rune's mana cost and
 * cooldown are applied when the ability is started this way.
 *
 * @author Mark Gottschling on 11/08/2026
 */
public class CastTestCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> rootArg;
    private final DefaultArg<String> slotArg;
    private final DefaultArg<String> modeArg;

    public CastTestCommand() {
        super("cast", "Test: cast a rune ability on yourself from code.");
        this.rootArg = withDefaultArg("root", "Root interaction id, e.g. Root_Ability_Fireball",
                ArgTypes.STRING, "Root_Ability_Fireball", "Root_Ability_Fireball");
        this.slotArg = withDefaultArg("slot", "Interaction type: Ability1-4, Primary, Secondary, Use",
                ArgTypes.STRING, "Ability1", "Ability1");
        this.modeArg = withDefaultArg("mode", "queue or try", ArgTypes.STRING, "queue", "queue");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String rootId = rootArg.get(context);
        RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
        if (root == null) {
            String abilities = RootInteraction.getAssetMap().getAssetMap().keySet().stream()
                    .filter(id -> id.contains("Ability"))
                    .sorted()
                    .collect(Collectors.joining(", "));
            context.sendMessage(Message.raw("No root interaction '" + rootId + "'. Abilities: " + abilities));
            return;
        }

        InteractionType type;
        try {
            type = InteractionType.valueOf(slotArg.get(context));
        } catch (IllegalArgumentException e) {
            context.sendMessage(Message.raw("Unknown interaction type '" + slotArg.get(context) + "'."));
            return;
        }

        InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        if (manager == null) {
            context.sendMessage(Message.raw("The player has no InteractionManager component."));
            return;
        }

        context.sendMessage(Message.raw("Mana before: " + mana(store, ref)
                + ", canRun(" + type + "): " + manager.canRun(type, root)));

        InteractionContext interactionContext = InteractionContext.forInteraction(manager, ref, type, store);
        String mode = modeArg.get(context).toLowerCase(Locale.ROOT);
        try {
            if (mode.equals("try")) {
                boolean[] started = {false};
                store.forEachChunk(ref, Query.<EntityStore>any(), (chunk, commandBuffer) ->
                        started[0] = manager.tryStartChain(ref, commandBuffer, type, interactionContext, root));
                context.sendMessage(Message.raw("tryStartChain(" + rootId + ") returned " + started[0] + "."));
            } else {
                InteractionChain chain = manager.initChain(type, interactionContext, root, false);
                manager.queueExecuteChain(chain);
                context.sendMessage(Message.raw("Queued " + rootId + " as " + type + ", state " + chain.getServerState() + "."));
                // report what happened once the manager has had a tick to run it
                world.execute(() -> context.sendMessage(Message.raw("After a tick: state " + chain.getServerState()
                        + ", mana " + mana(store, ref) + ".")));
            }
        } catch (RuntimeException e) {
            context.sendMessage(Message.raw("Cast failed: " + e));
        }
    }

    private static String mana(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return "?";
        }
        EntityStatValue mana = stats.get(DefaultEntityStatTypes.getMana());
        return mana == null ? "?" : mana.get() + "/" + mana.getMax();
    }
}
