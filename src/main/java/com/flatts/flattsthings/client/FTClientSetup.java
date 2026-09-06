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
     * Z by default.
     *
     * <p><b>Not V, and "vanilla does not use V" was simply wrong.</b> This was bound to V on that
     * belief until devbridge's {@code key} verb reported what the key actually owns:
     * {@code key.debug.dumpVersion}. Vanilla's F3 chords are ordinary key mappings, so they collide
     * for real - the controls screen shows the binding in red and the player is asked to resolve a
     * clash nobody meant to create. The whole set was then probed rather than guessed at again, in a
     * client with JEI and Jade loaded: G, H, N, B, C, X and T are vanilla's, R, U and F are JEI's,
     * and Z is free and the only unbound key within reach of WASD.
     *
     * <p><b>A key rather than only a config option, because this is the setting you change mid-job.</b>
     * The config switch is the pack author's, applies to everybody and needs a file edit; this is the
     * player's own, and the moment you want it is while you are standing in front of the block that
     * just swapped a tool you did not want. Anything that needs you to leave the game to change it
     * will not get changed.
     *
     */
    public static final KeyMapping TOGGLE_AUTO_SWAP = new KeyMapping(
        "key.flattsthings.toggle_auto_swap", GLFW.GLFW_KEY_Z, KeyMapping.Category.GAMEPLAY);

    private FTClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_AUTO_SWAP);
    }
}
