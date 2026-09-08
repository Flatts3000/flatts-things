package com.flatts.flattsthings.client;

import com.flatts.flattsthings.content.woodcutter.WoodcutterMenu;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
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
 * shapes; the most any wooden input offers is four, and the menu caps the list at
 * {@link WoodcutterMenu#MAX_OPTIONS} - which is eight, one grid row more than anything needs. A
 * scroller that can never scroll is a control that lies about what it does.
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
            int x = buttonX(index, this.leftPos);
            int y = buttonY(index, this.topPos);

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

    /**
     * <b>The plus one is not arbitrary.</b> The background blitted here is vanilla's
     * {@code stonecutter.png}, whose button recesses start one pixel down from the grid origin -
     * vanilla draws its sprite at {@code row * 18 + 1} and this did not, so every button sat a pixel
     * proud of the hole it belongs in. Invisible to every test in this repo, and obvious the moment
     * anybody looks at it.
     */
    private static int buttonX(int index, int leftPos) {
        return leftPos + GRID_X + (index % COLUMNS) * BUTTON_WIDTH;
    }

    private static int buttonY(int index, int topPos) {
        return topPos + GRID_Y + (index / COLUMNS) * BUTTON_HEIGHT + 1;
    }

    private static boolean isOver(int index, double mouseX, double mouseY, int leftPos,
                                  int topPos) {
        int x = buttonX(index, leftPos);
        int y = buttonY(index, topPos);
        return mouseX >= x && mouseX < x + BUTTON_WIDTH
            && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    /**
     * The name of whatever the cursor is over.
     *
     * <p>Without this an option is a picture with no label, and several of them are small wooden
     * shapes at sixteen pixels - a button, a slab and a stair are exactly the set a player would
     * want confirmed before spending a plank.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<ItemStack> options = this.menu.visibleOptions();
        for (int index = 0; index < options.size(); index++) {
            if (isOver(index, mouseX, mouseY, this.leftPos, this.topPos)) {
                graphics.setTooltipForNextFrame(this.font, options.get(index), mouseX, mouseY);
                return;
            }
        }
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
                    // The same click vanilla's cutter makes. A selection with no sound reads as a
                    // button that did not register.
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
