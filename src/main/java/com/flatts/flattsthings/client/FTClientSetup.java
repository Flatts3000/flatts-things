package com.flatts.flattsthings.client;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.registry.FTMenus;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

/** Client-side registration. MOD bus, client only. */
// No `bus` argument: 26.1 unified the buses and routes by event type, so the parameter is gone.
@EventBusSubscriber(modid = FlattsThings.MOD_ID, value = Dist.CLIENT)
public final class FTClientSetup {

    /**
     * V by default, which vanilla does not use.
     *
     * <p>Deliberately not a button on the inventory screen. Adding slots to vanilla's own
     * {@code InventoryMenu} is not something NeoForge exposes a hook for, and the alternatives are
     * a mixin or fighting the screen's layout. A key opens a screen, which is the boring option and
     * the one that keeps working.
     */
    public static final KeyMapping OPEN_TOOL_SLOTS = new KeyMapping(
        "key.flattsthings.tool_slots", GLFW.GLFW_KEY_V, KeyMapping.Category.INVENTORY);

    private FTClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TOOL_SLOTS);
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(FTMenus.TOOL_SLOTS.get(), ToolSlotsScreen::new);
    }
}
