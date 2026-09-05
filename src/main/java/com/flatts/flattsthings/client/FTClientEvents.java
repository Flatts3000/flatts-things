package com.flatts.flattsthings.client;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.network.OpenToolSlotsPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-side game events. GAME bus, client only. */
@EventBusSubscriber(modid = FlattsThings.MOD_ID, value = Dist.CLIENT)
public final class FTClientEvents {

    private FTClientEvents() {
    }

    /**
     * {@code while} rather than {@code if}: consumeClick drains a queue, so a key pressed twice
     * between ticks would otherwise open the screen once and leave the second press pending until
     * the next press, which reads as the key being unreliable.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (FTClientSetup.OPEN_TOOL_SLOTS.consumeClick()) {
            ClientPacketDistributor.sendToServer(new OpenToolSlotsPayload());
        }
    }
}
