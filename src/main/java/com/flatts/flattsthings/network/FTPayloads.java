package com.flatts.flattsthings.network;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.content.menu.ToolSlotsMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's network channel. One message so far, and it deliberately carries no data.
 *
 * <p>The handler touches the player only through the payload context, so no client-only class is
 * referenced from registration and this loads on a dedicated server.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class FTPayloads {

    private FTPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenToolSlotsPayload.TYPE, OpenToolSlotsPayload.STREAM_CODEC,
            FTPayloads::onOpenToolSlots);
    }

    /**
     * The sender is the authorisation. A player can only ever open their own slots, because the
     * container is built from {@code context.player()} and nothing in the message names anybody.
     */
    private static void onOpenToolSlots(OpenToolSlotsPayload payload, IPayloadContext context) {
        // THE SERVER DECIDES. The config is per-instance rather than synced, so a client that has
        // the feature on can be talking to a server that has it off, and the client's copy is not
        // the one that matters.
        if (!FTConfig.toolSlots()) {
            return;
        }
        context.enqueueWork(() -> context.player().openMenu(new SimpleMenuProvider(
            (containerId, inventory, player) -> new ToolSlotsMenu(containerId, inventory),
            Component.translatable("container.flattsthings.tool_slots"))));
    }
}
