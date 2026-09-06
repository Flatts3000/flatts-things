package com.flatts.flattsthings.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link Container} view of one player's tool slots, so vanilla's own slot machinery does the work.
 *
 * <p>Writing a menu against the attachment directly would mean reimplementing click handling, drag
 * splitting and shift-click, all of which vanilla already does correctly against a Container.
 *
 * <p><b>A live view of the attachment, not a copy, and it used to be a copy.</b> That was safe while
 * this backed a screen opened and closed in one go. It is not safe now: the same container backs
 * slots in {@code InventoryMenu}, which is built once and lives as long as the player, so a copy
 * taken at construction would be stale the first time the auto-swap moved a tool - and the menu
 * would then write that stale copy back over the swap, duplicating one tool and destroying another.
 *
 * <p>So every read goes to the attachment and every write goes straight back to it. Writes go
 * through {@link ToolSlots#writeAll}, which skips the per-slot validity check on purpose: vanilla
 * calls {@code setItem} mid-drag with contents no rule would allow to be placed, and the rule that
 * matters is enforced where a player can see it, in {@code ToolSlot.mayPlace}.
 */
public final class ToolSlotsContainer implements Container {

    private final Player player;

    public ToolSlotsContainer(Player player) {
        this.player = player;
    }

    private void write(int index, ItemStack stack) {
        List<ItemStack> stacks = new ArrayList<>(ToolSlots.SIZE);
        for (int slot = 0; slot < ToolSlots.SIZE; slot++) {
            stacks.add(slot == index ? stack : ToolSlots.get(this.player, slot).copy());
        }
        ToolSlots.writeAll(this.player, stacks);
    }

    @Override
    public int getContainerSize() {
        return ToolSlots.SIZE;
    }

    @Override
    public boolean isEmpty() {
        return ToolSlots.of(this.player).isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return ToolSlots.get(this.player, index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack present = ToolSlots.get(this.player, index).copy();
        if (present.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = present.split(count);
        this.write(index, present);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack removed = ToolSlots.get(this.player, index).copy();
        this.write(index, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.write(index, stack);
    }

    /**
     * Nothing to flush. Every mutation already went to the attachment, which is the only copy.
     */
    @Override
    public void setChanged() {
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
        ToolSlots.writeAll(this.player, Collections.nCopies(ToolSlots.SIZE, ItemStack.EMPTY));
    }
}
