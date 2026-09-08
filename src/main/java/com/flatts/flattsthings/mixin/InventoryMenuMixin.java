package com.flatts.flattsthings.mixin;

import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.content.ToolSlot;
import com.flatts.flattsthings.content.ToolSlotDisplay;
import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSlotsContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the tool slots in the inventory screen, which is the only place they were ever wanted.
 *
 * <p><b>The first version of this feature opened a screen of its own on a key press, and that was
 * the wrong call.</b> The ask was tools that live in the inventory without taking inventory space;
 * a second screen delivered the second half and quietly dropped the first. It was chosen to avoid
 * exactly this file, on the grounds that NeoForge exposes no hook for adding slots to
 * {@code InventoryMenu} - true, and not a reason to ship the wrong feature.
 *
 * <p><b>A mixin rather than an access transformer.</b> The alternative was making
 * {@code AbstractContainerMenu.addSlot} public and appending the slots after the fact. But the menu
 * is not built once: it is rebuilt on join, on respawn and on a dimension change, on both the client
 * and the server, and every one of those would need its own hook. Missing one leaves the two sides
 * disagreeing about how many slots there are, and {@code InventoryMenu} is synchronised by slot
 * INDEX - a disagreement moves items into the wrong slots rather than merely looking wrong. A
 * constructor runs for every one of those rebuilds, on both sides, and cannot be missed.
 *
 * <p><b>Appended at the end, indices 46 to 50, and that is what keeps vanilla working.</b> Vanilla's
 * {@code quickMoveStack} decides what a shift-click does from hardcoded index ranges up to 45.
 * Inserting anywhere earlier would shift the armour, inventory or offhand out from under those
 * ranges and break shift-clicking across the whole screen. Appending leaves every range intact, and
 * the method's final {@code else} already moves an unrecognised index into the inventory - so
 * shift-clicking a tool OUT of a tool slot works with nothing patched.
 *
 * <p>The slots are added unconditionally, whatever the config says. See {@link ToolSlot#isActive()}.
 */
@Mixin(InventoryMenu.class)
abstract class InventoryMenuMixin extends AbstractContainerMenu {

    private InventoryMenuMixin() {
        super(null, 0);
    }

    /** Vanilla's own slots, 0 to 45. Ours start here. */
    @Unique
    private static final int FLATTSTHINGS$FIRST_TOOL_SLOT = 46;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void flattsthings$addToolSlots(Inventory inventory, boolean active, Player owner,
                                           CallbackInfo callback) {
        ToolSlotsContainer container = new ToolSlotsContainer(owner);
        for (int index = 0; index < ToolSlots.SIZE; index++) {
            Slot slot = new ToolSlot(container, index);
            this.addSlot(slot);
        }
    }

    /**
     * Shift-clicking a tool in your inventory puts it in a tool slot.
     *
     * <p>The old standalone screen had this for free: shift-click in a two-container screen means
     * "send it to the other one". In the inventory screen it means "swap between inventory and
     * hotbar", and vanilla's own {@code quickMoveStack} has no idea our slots exist - it would move
     * a pickaxe to the hotbar and leave the tool slots empty. Losing that would be a step backwards
     * from a version players already had.
     *
     * <p><b>From the main inventory only, NOT the hotbar.</b> Covering the hotbar too was the first
     * version and it quietly broke a vanilla habit: shift-clicking a pickaxe off your hotbar moves
     * it to the inventory, and hijacking that sent it to a tool slot instead - and then
     * {@code doClick}'s repeat loop kept going until every tool slot was full. Taking something OUT
     * of the hotbar is the direction players already have a meaning for, so it is left alone. From
     * the hotbar it takes two shift-clicks, or one drag.
     *
     * <p>And only when a tool slot will actually take it. If every tool slot is full, or the item is
     * not tag-valid, this does nothing and vanilla's behaviour happens unchanged. So the vanilla
     * rule is not replaced, it is tried second.
     *
     * <p>The return contract matters and is easy to get wrong: {@code doClick} loops on
     * {@code quickMoveStack} while the returned stack is non-empty and the slot still holds the same
     * item, so returning the ORIGINAL copy is what lets a repeated shift-click keep going, and
     * returning empty is what stops it. Getting this wrong duplicates items rather than merely
     * misbehaving, which is why {@code shift_clicking_a_tool_into_a_slot_does_not_duplicate_it}
     * counts the axe everywhere afterwards.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void flattsthings$quickMoveIntoToolSlots(Player player, int index,
                                                     CallbackInfoReturnable<ItemStack> callback) {
        // ToolSlotDisplay TOO, AND LEAVING IT OUT WAS A REGRESSION. Hiding the slots is a
        // presentation change: moveItemStackTo consults mayPlace and never isActive, and the
        // creative inventory forwards a shift-click straight into this menu
        // (CreativeModeInventoryScreen.slotClicked -> player.inventoryMenu.clicked with QUICK_MOVE).
        // So with the slots hidden but this ungated, shift-clicking a tool on the creative screen
        // filed it into a slot nothing draws - it left the visible inventory and arrived nowhere the
        // player could see, recoverable only by switching to survival. Before the slots were hidden
        // it at least landed somewhere visible, so the hiding made this strictly worse.
        //
        // The rule is that a screen gets the panel AND the slots or neither; this is the second half
        // of it.
        if (!FTConfig.toolSlots()
            || !ToolSlotDisplay.shown()
            || index < InventoryMenu.INV_SLOT_START
            || index >= InventoryMenu.INV_SLOT_END) {
            return;
        }
        Slot source = this.slots.get(index);
        ItemStack stack = source.getItem();
        if (stack.isEmpty() || !ToolSlots.isValid(stack)) {
            return;
        }
        ItemStack before = stack.copy();
        if (!this.moveItemStackTo(stack, FLATTSTHINGS$FIRST_TOOL_SLOT,
                FLATTSTHINGS$FIRST_TOOL_SLOT + ToolSlots.SIZE, false)) {
            return;
        }
        if (stack.isEmpty()) {
            source.setByPlayer(ItemStack.EMPTY, before);
        } else {
            source.setChanged();
        }
        source.onTake(player, stack);
        callback.setReturnValue(before);
    }
}
