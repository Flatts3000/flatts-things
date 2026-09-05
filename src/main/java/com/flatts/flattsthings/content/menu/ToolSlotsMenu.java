package com.flatts.flattsthings.content.menu;

import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSlotsContainer;
import com.flatts.flattsthings.registry.FTMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The tool slots, plus the player's own inventory so things can be moved between them. */
public class ToolSlotsMenu extends AbstractContainerMenu {

    /** Layout, shared with the screen so the painted slots land under the real ones. */
    public static final int TOOL_ROW_X = 44;
    public static final int TOOL_ROW_Y = 20;
    public static final int INVENTORY_Y = 52;
    public static final int HOTBAR_Y = 110;

    private final Container tools;

    /** The client side, which builds its own container because it has no attachment to read. */
    public ToolSlotsMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new ToolSlotsContainer(inventory.player));
    }

    public ToolSlotsMenu(int containerId, Inventory inventory, Container tools) {
        super(FTMenus.TOOL_SLOTS.get(), containerId);
        this.tools = tools;

        for (int index = 0; index < ToolSlots.SIZE; index++) {
            this.addSlot(new ToolSlot(tools, index, TOOL_ROW_X + index * 18, TOOL_ROW_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9,
                    8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    /**
     * Shift-click. Tools go to the first free tool slot; anything else, and anything already in a
     * tool slot, goes back to the inventory.
     *
     * <p>The loop shape is vanilla's and so are its two easy mistakes: returning EMPTY when nothing
     * moved is what stops the caller spinning, and comparing against the ORIGINAL count is what
     * detects a partial move.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        int firstPlayerSlot = ToolSlots.SIZE;

        if (slotIndex < firstPlayerSlot) {
            if (!this.moveItemStackTo(inSlot, firstPlayerSlot, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(inSlot, 0, firstPlayerSlot, false)) {
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (inSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, inSlot);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.tools.stillValid(player);
    }

    /** A slot that only takes what the tag allows, and only one of it. */
    private static final class ToolSlot extends Slot {
        private ToolSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ToolSlots.isValid(stack);
        }

        /**
         * One per slot. Every vanilla tool already stacks to one, but a modded tool in the tag might
         * not, and a stack of sixteen pickaxes in a slot meant for the pickaxe you are using is not
         * what any of this is for.
         */
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
