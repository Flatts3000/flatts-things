package com.flatts.flattsthings.client;

import com.flatts.flattsthings.content.menu.ToolSlotsMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The tool slot screen.
 *
 * <p><b>Painted, with no texture of its own.</b> The panel is flat fills in vanilla's own palette and
 * the slots are vanilla's {@code container/slot} sprite, so this inherits the look for free and there
 * is no art asset to keep in step with a resource pack.
 *
 * <p>26.1 renamed the render path: {@code renderBg} is gone and the hook is
 * {@link #extractBackground}, taking a {@link GuiGraphicsExtractor}. Anything written against
 * {@code GuiGraphics} is for an older version.
 */
public class ToolSlotsScreen extends AbstractContainerScreen<ToolSlotsMenu> {

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    // Vanilla's container palette, so a painted panel and a textured one sit side by side.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;

    public ToolSlotsScreen(ToolSlotsMenu menu, Inventory inventory, Component title) {
        // Size goes through the constructor: imageWidth and imageHeight are final in 26.1 and can
        // no longer be assigned in the body the way older tutorials do it.
        super(menu, inventory, title, 176, 136);
        this.titleLabelY = 6;
        this.inventoryLabelY = 40;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
        super.extractBackground(graphics, mouseX, mouseY, alpha);
        int left = this.leftPos;
        int top = this.topPos;
        int right = left + this.imageWidth;
        int bottom = top + this.imageHeight;

        graphics.fill(left, top, right, bottom, PANEL);
        // A two-pixel bevel, light top-left and dark bottom-right, which is what makes a flat fill
        // read as a raised panel rather than a grey rectangle.
        graphics.fill(left, top, right - 2, top + 2, HIGHLIGHT);
        graphics.fill(left, top, left + 2, bottom - 2, HIGHLIGHT);
        graphics.fill(left + 2, bottom - 2, right, bottom, SHADOW);
        graphics.fill(right - 2, top + 2, right, bottom, SHADOW);

        // Every slot the menu actually has, rather than a hardcoded grid, so the painting cannot
        // drift from the layout the way two copies of a coordinate always eventually do.
        for (Slot slot : this.menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                left + slot.x - 1, top + slot.y - 1, 18, 18);
        }
    }
}
