package com.flatts.flattsthings.content;

import com.flatts.flattsthings.config.FTConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One tool slot, as a slot in vanilla's own inventory menu.
 *
 * <p>Being a real {@link Slot} is the entire design. Click handling, drag splitting, stack merging,
 * shift-click and the pickup rules are vanilla's, tested by everyone who has ever played the game,
 * and none of it had to be written here. The earlier version opened a screen of its own and got the
 * same behaviour only because it also went through a menu; this gets it in the screen players
 * already open.
 */
public final class ToolSlot extends Slot {

    /**
     * The ghost outline each empty slot shows, in the intended loadout order.
     *
     * <p><b>There are four of them for five slots, on purpose.</b> The first four name the tool
     * families these slots are for; the fifth is left blank because it is the free one - shears, a
     * second pickaxe, whatever you would rather carry - and vanilla ships no sprite for shears to
     * name it with anyway. A blank square next to four named ones reads as "anything", which is what
     * it is.
     *
     * <p><b>No sword.</b> These slots are for breaking blocks, not for fighting: a weapon is not
     * storable here at all (see {@code #flattsthings:tool_slot_valid}), so an outline promising one
     * would be an invitation the slot then refuses. It also matters for the auto-swap, which would
     * otherwise put a sword in your hand mid-swing.
     *
     * <p><b>A hint, not a rule.</b> Any item in {@code #flattsthings:tool_slot_valid} fits any slot -
     * putting a second pickaxe where the shovel outline is works fine. The icons exist because five
     * identical empty squares under the inventory tell a player nothing about what they are for, and
     * "a tool goes here" is the thing that needs saying.
     *
     * <p>All of them are vanilla's own sprites, so they match the armour outlines opposite them and
     * ship no art asset.
     */
    private static final Identifier[] ICONS = {
        Identifier.withDefaultNamespace("container/slot/pickaxe"),
        Identifier.withDefaultNamespace("container/slot/axe"),
        Identifier.withDefaultNamespace("container/slot/shovel"),
        Identifier.withDefaultNamespace("container/slot/hoe"),
    };

    public ToolSlot(Container container, int index) {
        super(container, index, ToolSlotLayout.slotX(index), ToolSlotLayout.slotY());
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return FTConfig.toolSlots() && ToolSlots.isValid(stack);
    }

    /**
     * <b>Also gated, and this is the half that makes the switch real.</b>
     *
     * <p>{@link #isActive()} is a CLIENT decision: it stops the slot being drawn and stops
     * {@code findSlot} returning it. Nothing on the server consults it - {@code doClick} checks
     * {@code mayPickup} and {@code mayPlace} and never {@code isActive} - and the config is COMMON,
     * so it is per-side and unsynced. Without this, a player on a server that had the feature off
     * could set it on in their own file and get working tool slots, because their client would
     * render and hit-test them and the server would honour the clicks.
     */
    @Override
    public boolean mayPickup(Player player) {
        return FTConfig.toolSlots() && super.mayPickup(player);
    }

    /**
     * One per slot. Every vanilla tool already stacks to one, but a modded tool in the tag might
     * not, and a stack of sixteen pickaxes in a slot meant for the pickaxe you are using is not what
     * any of this is for.
     */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /**
     * <b>The config switch acts here rather than on whether the slot exists.</b>
     *
     * <p>The obvious implementation - add the slots only when the feature is on - is a duplication
     * bug waiting to happen. {@code InventoryMenu} is synchronised by slot INDEX, so a server with
     * the feature off and a client with it on would disagree about how many slots there are, and
     * every slot after the disagreement would be a different slot on each side. That is not a
     * cosmetic desync; it moves items into the wrong places.
     *
     * <p>So the slots always exist and always exist in the same number, and this decides whether
     * they can be seen or touched. Vanilla honours it in all three places that matter: it skips
     * inactive slots when rendering, when drawing the empty-slot icon, and in {@code findSlot},
     * which is what a click looks through. A mismatched config then costs nothing worse than one
     * side refusing a click.
     *
     * <p>Anything already stored stays stored, and comes back when the switch goes back on.
     */
    @Override
    public boolean isActive() {
        return FTConfig.toolSlots() && ToolSlotDisplay.shown();
    }

    /**
     * <b>Vanilla draws this, not us.</b> {@code AbstractContainerScreen} blits the no-item icon for
     * any empty active slot, which is the same path the helmet and boots outlines take. Nothing in
     * this mod's own rendering is involved.
     *
     * <p>Returns null past the end of the array, which is both the fifth slot's blank square and the
     * guard against SIZE growing without new icons - a plain square rather than a throw while a
     * screen is drawing.
     */
    @Override
    public Identifier getNoItemIcon() {
        return this.getContainerSlot() < ICONS.length ? ICONS[this.getContainerSlot()] : null;
    }
}
