package com.flatts.flattsthings.client;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.content.ToolSlotLayout;
import com.flatts.flattsthings.content.ToolSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Paints the panel and the sunken squares under the tool slots on the inventory screen.
 *
 * <p><b>Only the background is ours.</b> The slots themselves are real slots in vanilla's
 * {@code InventoryMenu}, so the game draws the items in them, highlights the one under the cursor,
 * shows their tooltips and handles every click without being asked. All that is missing is what a
 * texture would normally provide, because vanilla's inventory texture stops at the bottom of its own
 * panel and ours hangs below it.
 *
 * <p><b>Painted rather than textured</b>, the same way the old standalone screen was: flat fills in
 * vanilla's own palette and vanilla's {@code container/slot} sprite. It inherits the look for free
 * and ships no art asset that a resource pack could leave stranded.
 *
 * <p><b>{@code ScreenEvent.Render.Background}, which is a narrow choice.</b>
 * {@code ContainerScreenEvent.Render.Foreground} is the obvious hook and is wrong: 26.1 fires it
 * after the slots are drawn, so the panel would be painted over the items sitting in it. There is no
 * {@code ContainerScreenEvent.Render.Background} in 26.1 - its own javadoc points here instead. This
 * one fires between the screen's background and its contents, which is exactly the gap wanted.
 *
 * <p>That hook is NOT translated by {@code leftPos}/{@code topPos}, unlike the foreground one, so
 * these coordinates are absolute and add the screen's origin by hand.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID, value = Dist.CLIENT)
public final class ToolSlotStrip {

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    // Vanilla's container palette, so the strip and the panel above it are the same grey.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;

    private ToolSlotStrip() {
    }

    @SubscribeEvent
    public static void onRenderBackground(ScreenEvent.Render.Background event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !FTConfig.toolSlots()) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int left = screen.getGuiLeft() + ToolSlotLayout.STRIP_X;
        int top = screen.getGuiTop() + ToolSlotLayout.STRIP_Y;
        int right = left + ToolSlotLayout.stripWidth();
        int bottom = top + ToolSlotLayout.stripHeight();

        graphics.fill(left, top, right, bottom, PANEL);
        // A two-pixel bevel, light top-left and dark bottom-right, which is what makes a flat fill
        // read as a raised panel rather than a grey rectangle.
        graphics.fill(left, top, right - 2, top + 2, HIGHLIGHT);
        graphics.fill(left, top, left + 2, bottom - 2, HIGHLIGHT);
        graphics.fill(left + 2, bottom - 2, right, bottom, SHADOW);
        graphics.fill(right - 2, top + 2, right, bottom, SHADOW);

        for (int index = 0; index < ToolSlots.SIZE; index++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                screen.getGuiLeft() + ToolSlotLayout.slotX(index) - 1,
                screen.getGuiTop() + ToolSlotLayout.slotY() - 1,
                ToolSlotLayout.SLOT, ToolSlotLayout.SLOT);
        }
    }
}
