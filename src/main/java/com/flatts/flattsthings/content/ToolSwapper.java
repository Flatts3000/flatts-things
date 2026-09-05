package com.flatts.flattsthings.content;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.registry.FTAttachments;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The auto-swap: hit a block, the right tool appears in your hand, and your own item comes back when
 * you stop.
 *
 * <p><b>The hook is {@code PlayerEvent.BreakSpeed}, fired once per block, and getting there took
 * two wrong answers.</b>
 *
 * <p>The first was {@code LeftClickBlock.START}, which reads as exactly right: it names the moment
 * digging begins. It is fired only from {@code MultiPlayerGameMode}, on the CLIENT. Nothing posts it
 * server-side in 26.1, so a server-side handler for it never runs at all. That was caught by driving
 * vanilla's real break path in a test rather than by posting the event by hand, which is the only
 * reason it is not still in here.
 *
 * <p>The second was swapping on every {@code BreakSpeed}. That fires server-side, but
 * {@code ServerPlayerGameMode} tracks destroy progress against the held item, so changing it every
 * tick is a reset every tick and the block never breaks.
 *
 * <p>So: {@code BreakSpeed}, which fires server-side each tick of a dig and carries the block being
 * hit, but acted on ONLY the first time a given position is seen. One swap per block, at the start,
 * where there is no progress yet to lose.
 *
 * <p><b>Server side only.</b> Every path here reads or writes an attachment, and the client's copy
 * is not the truth. The visible swap follows from the server changing the held item, the same way
 * it does when a hopper takes something out of your hand.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class ToolSwapper {

    /**
     * How long after the last sign of digging a stranded swap is unwound, in ticks.
     *
     * <p>STOP and ABORT cover an ordinary release. This covers everything else: a teleport, a death,
     * a block that vanished from under the cursor, a client that stopped sending. Without it a
     * player can be left holding a tool they never chose with their own item nowhere in sight.
     */
    private static final int STRANDED_AFTER_TICKS = 40;

    /** Last tick each player showed signs of digging. Transient: a restart unwinds via login. */
    private static final Map<UUID, Integer> LAST_ACTIVITY = new ConcurrentHashMap<>();

    /** The block each player was last seen digging, so a new one can be told from a continuing one. */
    private static final Map<UUID, BlockPos> LAST_TARGET = new ConcurrentHashMap<>();

    private ToolSwapper() {
    }

    /**
     * The one hook that both fires server-side and knows which block is being hit.
     *
     * <p>Acted on once per position. The position is what makes "the start of a dig" observable from
     * an event that fires every tick of one.
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        LAST_ACTIVITY.put(player.getUUID(), player.tickCount);
        event.getPosition().ifPresent(pos -> {
            BlockPos previous = LAST_TARGET.put(player.getUUID(), pos.immutable());
            if (!pos.equals(previous)) {
                swapIn(player, event.getState());
            }
        });
    }

    @SubscribeEvent
    public static void onBreakBlock(BreakBlockEvent event) {
        swapOut(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.getData(FTAttachments.TOOL_SWAP).active()) {
            return;
        }
        int last = LAST_ACTIVITY.getOrDefault(player.getUUID(), player.tickCount);
        if (player.tickCount - last > STRANDED_AFTER_TICKS) {
            swapOut(player);
        }
    }

    /**
     * Unwind anything a crash left behind.
     *
     * <p>This is the reason {@link ToolSwap} is serialised. Without it, a server that went down
     * mid-swing would bring the player back holding a tool with their own item gone.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        swapOut(event.getEntity());
    }

    public static void swapIn(Player player, net.minecraft.world.level.block.state.BlockState state) {
        if (player.getData(FTAttachments.TOOL_SWAP).active()) {
            return;
        }
        int slot = ToolSlots.bestSlotFor(player, state);
        if (slot < 0) {
            return;
        }
        int hotbarSlot = player.getInventory().getSelectedSlot();
        ItemStack tool = ToolSlots.get(player, slot).copy();
        ItemStack displaced = player.getInventory().getItem(hotbarSlot).copy();

        ToolSlots.set(player, slot, ItemStack.EMPTY);
        player.getInventory().setItem(hotbarSlot, tool);
        player.setData(FTAttachments.TOOL_SWAP, new ToolSwap(slot, hotbarSlot, displaced));
    }

    /**
     * Put everything back, and lose nothing doing it.
     *
     * <p>The tool may have been damaged or destroyed while it was out, and the player may have moved
     * something into the hotbar slot in the meantime, so what comes back is whatever is there now
     * rather than what was taken. If that no longer belongs in a tool slot it goes to the inventory,
     * and if the inventory is full it is dropped, because the one outcome this must never have is
     * quietly deleting an item.
     */
    public static void swapOut(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        ToolSwap swap = player.getData(FTAttachments.TOOL_SWAP);
        if (!swap.active()) {
            return;
        }
        player.setData(FTAttachments.TOOL_SWAP, ToolSwap.NONE);
        LAST_ACTIVITY.remove(player.getUUID());
        LAST_TARGET.remove(player.getUUID());

        ItemStack inHand = player.getInventory().getItem(swap.hotbarSlot()).copy();
        player.getInventory().setItem(swap.hotbarSlot(), swap.displaced());

        if (inHand.isEmpty()) {
            return;
        }
        if (ToolSlots.isValid(inHand) && ToolSlots.get(player, swap.toolSlot()).isEmpty()) {
            ToolSlots.set(player, swap.toolSlot(), inHand);
        } else if (!player.getInventory().add(inHand)) {
            player.drop(inHand, false);
        }
    }
}
