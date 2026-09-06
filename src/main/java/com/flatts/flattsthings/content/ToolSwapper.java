package com.flatts.flattsthings.content;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.registry.FTAttachments;
import java.util.Map;
import java.util.Optional;
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
 * <p><b>The hook is {@code PlayerEvent.BreakSpeed}, acted on once per dig, and getting there took
 * three wrong answers.</b>
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
 * <p>The third was telling one dig from the next by position alone, which is what shipped in #27
 * and did not work in a real client at all. See {@link #DIGGING}.
 *
 * <p>So: {@code BreakSpeed}, which fires server-side each tick of a dig and carries the block being
 * hit, acted on only on a dig's first tick. One swap per dig, at the start, where there is no
 * progress yet to lose.
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

    /**
     * How long a gap in {@code BreakSpeed} ends a dig, in ticks.
     *
     * <p>The event fires every server tick for as long as a block is being broken, so any gap at
     * all means the player let go. Five ticks is a quarter of a second: long enough that a stutter
     * is not read as a new dig, short enough that a player who releases and clicks the same block
     * again gets their tool.
     */
    private static final int DIG_GAP_TICKS = 5;

    /** Where a player was digging and when they were last seen at it. */
    private record Dig(int tick, BlockPos pos) {
    }

    /**
     * The dig each player is in the middle of.
     *
     * <p><b>Both halves are needed and the first version had only the position.</b> It compared the
     * block against the last one seen and swapped when it changed, which is wrong the moment a dig
     * ends without a swap: the position stayed recorded, and every later dig on that same block
     * refused to swap for the rest of the session. Digging with the right tool already in hand is
     * exactly that case, so the feature quietly stopped working on any block a player had touched.
     * The tick is what tells one dig from the next, rather than the position alone.
     *
     * <p>Transient: a restart unwinds via login.
     */
    private static final Map<UUID, Dig> DIGGING = new ConcurrentHashMap<>();

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
        if (player.level().isClientSide() || !swapping(player)) {
            return;
        }
        Optional<BlockPos> position = event.getPosition();
        if (position.isEmpty()) {
            // No block to key on, but the player is plainly still swinging, so keep the stranded
            // backstop from unwinding a live swap underneath them.
            DIGGING.computeIfPresent(player.getUUID(),
                (uuid, dig) -> new Dig(player.tickCount, dig.pos()));
            return;
        }
        BlockPos pos = position.get().immutable();
        Dig previous = DIGGING.get(player.getUUID());
        if (startsANewDig(player, previous, pos)) {
            swapIn(player, event.getState());
        }
        // RECORDED AFTER the swap, not before. swapIn may unwind a previous swap on its way in, and
        // unwinding clears this record - so writing it first meant the record vanished exactly when
        // the player moved from one block to another, and forty ticks later the stranded backstop
        // fired and took their tool away mid-dig.
        DIGGING.put(player.getUUID(), new Dig(player.tickCount, pos));
    }

    /**
     * Whether this is the first tick of a dig rather than the continuation of one.
     *
     * <p>A different block is obviously new. So is the same block after a gap, which is what makes
     * releasing and clicking again work. A negative gap means the player respawned and their tick
     * count restarted, which is also a new dig by any reading.
     */
    private static boolean startsANewDig(Player player, Dig previous, BlockPos pos) {
        if (previous == null || !pos.equals(previous.pos())) {
            return true;
        }
        int elapsed = player.tickCount - previous.tick();
        return elapsed < 0 || elapsed > DIG_GAP_TICKS;
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
        // TURNED OFF MID-SWING STILL GIVES THE ITEM BACK. The displaced stack lives only in the
        // attachment while a swap is live, so a gate that merely stopped new swaps would strand
        // whatever the player was holding the moment somebody edited the config.
        if (!swapping(player)) {
            swapOut(player);
            return;
        }
        Dig dig = DIGGING.get(player.getUUID());
        if (dig == null) {
            // A live swap always has a dig recorded, because swapIn only runs from the hook that
            // writes one. Missing means the record was dropped, which is itself stranded.
            swapOut(player);
            return;
        }
        int elapsed = player.tickCount - dig.tick();
        if (elapsed < 0 || elapsed > STRANDED_AFTER_TICKS) {
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

    /**
     * Unwind on the way out, and drop the dig record with it.
     *
     * <p>Login already covers a crash. This covers an ordinary quit, so a player is not saved
     * mid-swap, and it is what keeps {@link #DIGGING} from growing one entry per player who has
     * ever mined on a long-running server.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        swapOut(event.getEntity());
        DIGGING.remove(event.getEntity().getUUID());
    }

    /**
     * Whether this player is currently having tools swapped for them.
     *
     * <p><b>Two answers, both of which must be yes, and they are different questions.</b> The config
     * is the pack author's and applies to everyone; the attachment is the player's own and is flipped
     * with a key. Reading them together here rather than at each call site is what stops one being
     * checked and the other forgotten - the bug that would look like a key that works everywhere
     * except the one path nobody tested.
     */
    public static boolean swapping(Player player) {
        return FTConfig.toolAutoSwap() && player.getData(FTAttachments.AUTO_SWAP_WANTED);
    }

    /**
     * Flip this player's own preference, and say what it became.
     *
     * <p>Unwinds a live swap on the way out, for the same reason the config gate does: while a swap
     * is in progress the player's own item exists only in the attachment, so switching off without
     * unwinding strands it.
     */
    public static boolean toggleWanted(Player player) {
        boolean wanted = !player.getData(FTAttachments.AUTO_SWAP_WANTED);
        player.setData(FTAttachments.AUTO_SWAP_WANTED, wanted);
        if (!wanted) {
            swapOut(player);
        }
        return wanted;
    }

    public static void swapIn(Player player, net.minecraft.world.level.block.state.BlockState state) {
        int slot = ToolSlots.bestSlotFor(player, state);
        if (slot < 0) {
            // Nothing beats what is in hand. If that is because a swap already put the right tool
            // there, leaving it alone is the answer - re-swapping the same tool every block would be
            // visible fidgeting for no gain.
            return;
        }
        // A DIFFERENT BLOCK CAN WANT A DIFFERENT TOOL, and the first version could not say so. It
        // refused outright whenever a swap was live, so starting on a log and sweeping onto dirt
        // kept the axe - or, once the stranded backstop fired, left the player digging bare-handed
        // with their own item put back. Found by doing exactly that: break a log, move to dirt.
        //
        // Unwinding first is what keeps the accounting honest. The displaced item goes back to the
        // hand and the old tool to its slot before anything new is taken, so at no point are two
        // tools out of their slots or one item recorded as displaced twice.
        if (player.getData(FTAttachments.TOOL_SWAP).active()) {
            swapOut(player);
            slot = ToolSlots.bestSlotFor(player, state);
            if (slot < 0) {
                return;
            }
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
        // UNCONDITIONALLY, BEFORE THE EARLY RETURN. The dig record outliving the dig is the bug
        // this method used to cause: clearing it only when a swap was active left every
        // swap-less dig recorded forever.
        DIGGING.remove(player.getUUID());

        ToolSwap swap = player.getData(FTAttachments.TOOL_SWAP);
        if (!swap.active()) {
            return;
        }
        player.setData(FTAttachments.TOOL_SWAP, ToolSwap.NONE);

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
