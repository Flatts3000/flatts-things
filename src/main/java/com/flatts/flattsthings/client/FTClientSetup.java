package com.flatts.flattsthings.client;

import com.flatts.flattsthings.FlattsThings;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.flatts.flattsthings.registry.FTMenus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

/** Client-side registration. MOD bus, client only. */
// No `bus` argument: 26.1 unified the buses and routes by event type, so the parameter is gone.
@EventBusSubscriber(modid = FlattsThings.MOD_ID, value = Dist.CLIENT)
public final class FTClientSetup {

    /**
     * This mod's own category in the Key Binds screen.
     *
     * <p><b>Its own, rather than borrowing vanilla's Gameplay.</b> A binding filed under Gameplay is
     * sorted in among two dozen of the game's own and there is nothing to say which mod put it
     * there, so a player looking for it has to know its name already. A named category is how every
     * other mod is found in that screen.
     *
     * <p>Constructed rather than registered through {@code KeyMapping.Category.register}, which is
     * deprecated in 26.1 in favour of {@link RegisterKeyMappingsEvent#registerCategory}. The label
     * comes from {@code id.toLanguageKey("key.category")}, so this identifier fixes the lang key as
     * {@code key.category.flattsthings.flattsthings} - rename the identifier and the screen shows
     * the raw key instead of a name.
     */
    public static final KeyMapping.Category CATEGORY =
        new KeyMapping.Category(FlattsThings.KEY_CATEGORY);

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
        "key.flattsthings.toggle_auto_swap", GLFW.GLFW_KEY_Z, CATEGORY);

    private FTClientSetup() {
    }

    /**
     * The woodcutter's screen, bound to its menu type.
     *
     * <p>Without this the block opens a menu the client has no screen for, which is a crash rather
     * than a blank window - the one failure in this feature that no server-side test can see.
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FTMenus.WOODCUTTER.get(), WoodcutterScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // The category first: a mapping naming a category nobody registered is filed nowhere.
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_AUTO_SWAP);
    }
}
