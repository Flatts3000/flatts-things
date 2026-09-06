package com.flatts.flattsthings.client;

import com.flatts.flattsthings.FlattsThings;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client-side registration. MOD bus, client only. */
// No `bus` argument: 26.1 unified the buses and routes by event type, so the parameter is gone.
@EventBusSubscriber(modid = FlattsThings.MOD_ID, value = Dist.CLIENT)
public final class FTClientSetup {

    /**
     * V by default, which vanilla does not use.
     *
     * <p><b>A key rather than only a config option, because this is the setting you change mid-job.</b>
     * The config switch is the pack author's, applies to everybody and needs a file edit; this is the
     * player's own, and the moment you want it is while you are standing in front of the block that
     * just swapped a tool you did not want. Anything that needs you to leave the game to change it
     * will not get changed.
     *
     * <p>There is no screen to open any more - the tool slots are in the inventory - so V, which this
     * mod already used, is free again for the one thing left worth binding.
     */
    public static final KeyMapping TOGGLE_AUTO_SWAP = new KeyMapping(
        "key.flattsthings.toggle_auto_swap", GLFW.GLFW_KEY_V, KeyMapping.Category.GAMEPLAY);

    private FTClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTO_SWAP);
    }
}
