package com.flatts.flattsthings.network;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.content.ToolSwapper;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's network channel. One message, and it deliberately carries no data.
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
        registrar.playToServer(ToggleAutoSwapPayload.TYPE, ToggleAutoSwapPayload.STREAM_CODEC,
            FTPayloads::onToggleAutoSwap);
    }

    /**
     * The sender is the authorisation. A player can only ever flip their own setting, because the
     * value is read from {@code context.player()} and nothing in the message names anybody.
     *
     * <p><b>Public so a test can reach it, and that is a deliberate trade</b> - the same one
     * {@code FTConfig.switchFor} makes, with the same warning attached: this exists for the tests and
     * gameplay code must not call it. The alternative was leaving the whole server side of the
     * keybind at zero coverage, because the only other caller is the network layer delivering a
     * packet and a GameTest has no client to send one.
     *
     * <p>Package-private would have been tighter and was tried. It fails for a reason worth writing
     * down: it would put the test in this package, and the coverage gate excludes {@code gametest/**}
     * only - so a test class living here would be measured as production code. Keeping every GameTest
     * in one package is worth one public method.
     *
     * <p><b>Says what happened, every time.</b> A key that silently changes a setting you cannot see
     * is a key players press twice and then distrust. When a pack has the feature switched off it
     * says that instead of pretending to toggle, because the alternative is a player flipping a
     * setting that will not take effect and having no way to find out why.
     */
    public static void onToggleAutoSwap(ToggleAutoSwapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!FTConfig.toolAutoSwap()) {
                context.player().sendOverlayMessage(
                    Component.translatable("message.flattsthings.auto_swap.unavailable"));
                return;
            }
            boolean wanted = ToolSwapper.toggleWanted(context.player());
            // sendOverlayMessage is the action bar, above the hotbar: 26.1's name for what used to
            // be displayClientMessage(component, true). The chat would be the wrong place - this is
            // a setting you flip repeatedly, and repeated chat lines are noise.
            context.player().sendOverlayMessage(Component.translatable(wanted
                ? "message.flattsthings.auto_swap.on"
                : "message.flattsthings.auto_swap.off"));
        });
    }
}
