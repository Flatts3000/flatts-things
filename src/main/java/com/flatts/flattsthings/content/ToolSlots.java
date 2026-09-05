package com.flatts.flattsthings.content;

import com.flatts.flattsthings.registry.FTAttachments;
import com.flatts.flattsthings.registry.FTTags;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A player's dedicated tool slots: storage that is not part of inventory space.
 *
 * <p>The hotbar is nine slots, and a pickaxe, axe, shovel, sword and shears is five of them before
 * you have picked up a single block. These slots hold the tools so the hotbar can hold the game.
 *
 * <p>This class is the storage half only. Putting things in through a screen, and reaching for the
 * right one when you break something, are separate slices of issue #24.
 */
public final class ToolSlots {

    /**
     * Five, which is a design decision rather than a round number.
     *
     * <p>It covers a pickaxe, an axe, a shovel and a sword with one left over, so a player still
     * chooses between a hoe and shears rather than being handed the whole set. Freeing the hotbar
     * is the point; removing every decision about what to carry is not.
     *
     * <p><b>REDUCING THIS NUMBER DELETES ITEMS FROM EXISTING SAVES.</b> {@link #fromList} truncates
     * anything past the end, because the alternative is refusing to load a world. Raising it is
     * safe and pads with empty.
     */
    public static final int SIZE = 5;

    public static final Codec<ToolSlots> CODEC =
        ItemStack.OPTIONAL_CODEC.listOf().xmap(ToolSlots::fromList, ToolSlots::asList);

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public ToolSlots() {
    }

    static ToolSlots fromList(List<ItemStack> saved) {
        ToolSlots slots = new ToolSlots();
        for (int index = 0; index < Math.min(SIZE, saved.size()); index++) {
            slots.items.set(index, saved.get(index).copy());
        }
        return slots;
    }

    private List<ItemStack> asList() {
        return List.copyOf(this.items);
    }

    /** What may be put in a slot. An empty stack is always allowed, because that is how you clear one. */
    public static boolean isValid(ItemStack stack) {
        return stack.isEmpty() || stack.is(FTTags.TOOL_SLOT_VALID);
    }

    public ItemStack get(int index) {
        return this.items.get(index);
    }

    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    public ToolSlots copy() {
        return fromList(this.asList());
    }

    /**
     * Replace every slot at once, skipping the per-slot validity check.
     *
     * <p>For {@link ToolSlotsContainer} only. A menu enforces what may be placed through
     * {@code Slot.mayPlace}, which runs before the stack ever reaches the container, so re-checking
     * here would reject legitimate vanilla shuffling (a swap mid-drag briefly holds odd contents)
     * while adding nothing.
     */
    static void writeAll(Player player, List<ItemStack> stacks) {
        player.setData(FTAttachments.TOOL_SLOTS, fromList(stacks));
    }

    // ---------------- player-facing access ----------------

    public static ToolSlots of(Player player) {
        return player.getData(FTAttachments.TOOL_SLOTS);
    }

    public static ItemStack get(Player player, int index) {
        return of(player).get(index);
    }

    /**
     * Put a stack in a slot, returning what was there.
     *
     * <p><b>Writes the attachment back rather than mutating the stored instance.</b> That is
     * deliberate but it is NOT a proven fix for a known bug, and the difference matters. Mutating in
     * place was tried and no test in this suite noticed, because the question is whether NeoForge
     * persists an attachment it was never told changed, and answering it needs a real save and
     * reload rather than a GameTest.
     *
     * <p>So this is defensive rather than demonstrated: copy, write, and let the attachment system
     * be told plainly, instead of depending on internal dirty-tracking behaviour that this code
     * should not have to know. If someone later proves in-place mutation is safe, this can be
     * simplified and the reasoning here deleted with it.
     *
     * @throws IllegalArgumentException if the stack is not allowed in a tool slot
     */
    public static ItemStack set(Player player, int index, ItemStack stack) {
        if (!isValid(stack)) {
            throw new IllegalArgumentException(
                stack.getItem() + " is not #flattsthings:tool_slot_valid");
        }
        ToolSlots slots = of(player).copy();
        ItemStack previous = slots.items.get(index);
        slots.items.set(index, stack.copy());
        player.setData(FTAttachments.TOOL_SLOTS, slots);
        return previous;
    }
}
