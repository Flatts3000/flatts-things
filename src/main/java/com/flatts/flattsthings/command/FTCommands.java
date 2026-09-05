package com.flatts.flattsthings.command;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.menu.ToolSlotsMenu;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /toolslots}, which opens the tool slot screen for whoever ran it.
 *
 * <p>The key is the way players open this. The command exists for the two cases a key cannot serve:
 * a pack or accessibility setup that wants a different route in, and this repo's own visual
 * verification, since devbridge can drive a command but cannot press a keybind.
 *
 * <p>Permission level 0 on purpose. It opens the caller's own storage and can reach nobody else's,
 * so requiring op would only stop players using it on servers.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class FTCommands {

    private FTCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("toolslots")
            .requires(source -> source.isPlayer())
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                if (player == null) {
                    return 0;
                }
                player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, who) -> new ToolSlotsMenu(containerId, inventory),
                    Component.translatable("container.flattsthings.tool_slots")));
                return Command.SINGLE_SUCCESS;
            }));
    }
}
