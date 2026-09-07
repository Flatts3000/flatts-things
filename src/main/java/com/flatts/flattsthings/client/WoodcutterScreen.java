package com.flatts.flattsthings.client;

import com.flatts.flattsthings.content.woodcutter.WoodcutterMenu;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The woodcutter's screen.
 *
 * <p><b>It borrows vanilla's stonecutter art rather than shipping any.</b> The background and the
 * three button states are sprites the game already has, referenced by path, so this adds no texture a
 * resource pack could leave stranded - the same reasoning that keeps the tool slot strip painted from
 * vanilla's own slot sprite.
 *
 * <p><b>No scroll bar, deliberately.</b> Vanilla's cutter needs one because stone has dozens of
 * shapes; wood has two, and the menu caps the list at {@link WoodcutterMenu#MAX_OPTIONS}. A scroller
 * that can never scroll is a control that lies about what it does.
 *
 * <p><b>This class cannot be tested.</b> No GameTest and no JUnit test reaches a line of it, which is
 * why {@code client/**} is excluded from the coverage gate. It is verified by looking at it, through
 * devbridge.
 */
public class WoodcutterScreen extends AbstractContainerScreen<WoodcutterMenu> {

    private static final Identifier BACKGROUND =
        Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final Identifier BUTTON =
        Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private static final Identifier BUTTON_SELECTED =
        Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final Identifier BUTTON_HOVERED =
        Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");

    /** Where vanilla's cutter puts its first button, so the panel behind it lines up. */
    private static final int GRID_X = 52;
    private static final int GRID_Y = 14;
    private static final int COLUMNS = 4;
    private static final int BUTTON_WIDTH = 16;
    private static final int BUTTON_HEIGHT = 18;

    public WoodcutterScreen(WoodcutterMenu menu, Inventory inventory, Component title) {
        // imageWidth and imageHeight are FINAL in 26.1 and cannot be assigned in the body; they come
        // through the constructor instead. 176x166 is vanilla's container size.
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        graphics.blit(pipeline, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
            this.imageWidth, this.imageHeight, 256, 256);

        List<ItemStack> options = this.menu.visibleOptions();
        for (int index = 0; index < options.size(); index++) {
            int x = this.leftPos + GRID_X + (index % COLUMNS) * BUTTON_WIDTH;
            int y = this.topPos + GRID_Y + (index / COLUMNS) * BUTTON_HEIGHT;

            Identifier sprite = BUTTON;
            if (index == this.menu.selectedIndex()) {
                sprite = BUTTON_SELECTED;
            } else if (isOver(index, mouseX, mouseY, this.leftPos, this.topPos)) {
                sprite = BUTTON_HOVERED;
            }
            graphics.blitSprite(pipeline, sprite, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
            // Drawn in the same pass as the button, in absolute coordinates, which is how vanilla's
            // cutter does it. There is no separate label pass to hang these off in 26.1.
            graphics.item(options.get(index), x, y + 1);
        }
    }

    private static boolean isOver(int index, double mouseX, double mouseY, int leftPos,
                                  int topPos) {
        int x = leftPos + GRID_X + (index % COLUMNS) * BUTTON_WIDTH;
        int y = topPos + GRID_Y + (index / COLUMNS) * BUTTON_HEIGHT;
        return mouseX >= x && mouseX < x + BUTTON_WIDTH
            && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    /**
     * A click on a button becomes a menu button press, which is what the server is listening for.
     *
     * <p>The option slots exist so the CONTENTS sync; they refuse pickup, so clicking one does
     * nothing through the ordinary slot path. This turns that click into the same
     * {@code clickMenuButton} vanilla's cutter sends.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubleClick) {
        List<ItemStack> options = this.menu.visibleOptions();
        for (int index = 0; index < options.size(); index++) {
            if (isOver(index, event.x(), event.y(), this.leftPos, this.topPos)) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId, index);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
