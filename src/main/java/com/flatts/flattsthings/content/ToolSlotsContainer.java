package com.flatts.flattsthings.content;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link Container} view of one player's tool slots, so vanilla's own slot machinery does the work.
 *
 * <p>Writing a menu against the attachment directly would mean reimplementing click handling, drag
 * splitting and shift-click, all of which vanilla already does correctly against a Container.
 *
 * <p>It holds a working copy and writes the whole set back on {@link #setChanged()}. Vanilla calls
 * that after every mutation, so the attachment is never behind, and the copy means a half-finished
 * drag cannot leave the stored slots in a state nobody asked for.
 */
public final class ToolSlotsContainer implements Container {

    private final Player player;
    private final NonNullList<ItemStack> working =
        NonNullList.withSize(ToolSlots.SIZE, ItemStack.EMPTY);

    public ToolSlotsContainer(Player player) {
        this.player = player;
        ToolSlots stored = ToolSlots.of(player);
        for (int index = 0; index < ToolSlots.SIZE; index++) {
            this.working.set(index, stored.get(index).copy());
        }
    }

    @Override
    public int getContainerSize() {
        return ToolSlots.SIZE;
    }

    @Override
    public boolean isEmpty() {
        return this.working.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.working.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack removed = ContainerHelper.removeItem(this.working, index, count);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack removed = ContainerHelper.takeItem(this.working, index);
        this.setChanged();
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.working.set(index, stack);
        this.setChanged();
    }

    @Override
    public void setChanged() {
        ToolSlots.writeAll(this.player, this.working);
    }

    /**
     * Only the owner. These slots are the player's own storage rather than a block's, so there is no
     * distance check to make and no second player who could ever be looking at them.
     */
    @Override
    public boolean stillValid(Player who) {
        return who == this.player;
    }

    @Override
    public void clearContent() {
        this.working.clear();
        this.setChanged();
    }
}
