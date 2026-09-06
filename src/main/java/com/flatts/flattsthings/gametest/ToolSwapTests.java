package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSwapper;
import com.flatts.flattsthings.registry.FTAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The auto-swap, and above all the promise that nothing is lost doing it.
 *
 * <p>Slice three of #24. The interesting tests here are not "does the right tool appear" but the
 * accounting ones: a tool exists exactly once, and the item the player was carrying always comes
 * back. Recompile learned the same lesson on the Scrap Hauler, where six separate code paths could
 * each break a once-only invariant and each needed its own test.
 */
final class ToolSwapTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos TARGET = new BlockPos(1, 1, 1);

    private ToolSwapTests() {
    }

    /** Count one item across every place it could be, which is how a duplication bug shows up. */
    private static int countEverywhere(ServerPlayer player, Item item) {
        int total = 0;
        for (int index = 0; index < ToolSlots.SIZE; index++) {
            if (ToolSlots.get(player, index).is(item)) {
                total++;
            }
        }
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            if (player.getInventory().getItem(index).is(item)) {
                total++;
            }
        }
        return total;
    }

    static void register() {
        BlockState stone = Blocks.STONE.defaultBlockState();

        FTGameTests.test("a_better_tool_swaps_into_the_hand", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 12));

            ToolSwapper.swapIn(player, stone);

            helper.assertTrue(player.getMainHandItem().is(Items.DIAMOND_PICKAXE),
                "the pickaxe should be in hand, found " + player.getMainHandItem().getItem());
            helper.assertTrue(ToolSlots.get(player, 0).isEmpty(),
                "the tool slot is empty while the tool is out");
            helper.succeed();
        });

        // THE ACCOUNTING TEST. A swap that copies rather than moves is the easiest bug to write
        // here and the hardest to notice, because everything looks right until someone counts.
        FTGameTests.test("the_tool_exists_exactly_once_while_swapped", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.DIAMOND_PICKAXE));
            ToolSwapper.swapIn(player, stone);

            int found = countEverywhere(player, Items.DIAMOND_PICKAXE);
            helper.assertTrue(found == 1, "expected exactly one pickaxe, found " + found);
            helper.succeed();
        });

        FTGameTests.test("everything_goes_back_when_the_swap_ends", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            int hotbar = player.getInventory().getSelectedSlot();
            ToolSlots.set(player, 0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(hotbar, new ItemStack(Items.COBBLESTONE, 12));

            ToolSwapper.swapIn(player, stone);
            ToolSwapper.swapOut(player);

            helper.assertTrue(ToolSlots.get(player, 0).is(Items.DIAMOND_PICKAXE),
                "the pickaxe must return to its slot");
            ItemStack back = player.getInventory().getItem(hotbar);
            helper.assertTrue(back.is(Items.COBBLESTONE) && back.getCount() == 12,
                "the displaced stack must come back whole, found " + back.getCount() + " of "
                    + back.getItem());
            helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                "the swap must be cleared afterwards");
            helper.succeed();
        });

        // Ties go to the hand: swapping to something no better is visible fidgeting for no gain.
        FTGameTests.test("no_swap_when_the_held_tool_is_already_as_good", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.WOODEN_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.NETHERITE_PICKAXE));

            ToolSwapper.swapIn(player, stone);

            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "a worse tool must not replace a better one");
            helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                "no swap should have been recorded");
            helper.succeed();
        });

        // The crash path. This is why ToolSwap is serialised rather than a map in memory.
        FTGameTests.test("a_stranded_swap_is_unwound_and_loses_nothing", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            int hotbar = player.getInventory().getSelectedSlot();
            ToolSlots.set(player, 0, new ItemStack(Items.IRON_PICKAXE));
            player.getInventory().setItem(hotbar, new ItemStack(Items.TORCH, 30));
            ToolSwapper.swapIn(player, stone);

            // Login, the stranded timer and an ordinary stop all run this same path.
            ToolSwapper.swapOut(player);

            helper.assertTrue(ToolSlots.get(player, 0).is(Items.IRON_PICKAXE), "the tool came back");
            helper.assertTrue(player.getInventory().getItem(hotbar).is(Items.TORCH),
                "the torches came back");
            helper.assertTrue(countEverywhere(player, Items.IRON_PICKAXE) == 1,
                "still exactly one pickaxe after unwinding");
            helper.succeed();
        });

        // TESTED THROUGH VANILLA'S OWN BREAK PATH rather than by posting an event, and that
        // distinction found a real bug: the first version hooked LeftClickBlock.START, which reads
        // as exactly the right moment and is fired only on the CLIENT. A server-side handler for it
        // never runs. Posting the event by hand hid that completely.
        //
        // WHAT THIS CANNOT PROVE is that the dig then runs to completion. A mock player's destroy
        // loop is not driven by client packets, so the block does not break here however correct the
        // swap is. Confirmed by diagnostic rather than assumed: the swap fires and the pickaxe is in
        // hand, and the block is still standing twenty ticks later. That half needs a human at the
        // keyboard, and the issue is labelled for it.
        //
        // ServerPlayerGameMode tracks isDestroyingBlock and destroyProgressStart, and changing the
        // held item interrupts digging. Swapping on every BreakSpeed tick therefore resets progress
        // every tick and the block never breaks - which looks like the mod having broken mining. The
        // swap happens once, at START, before any progress exists to lose. This drives
        // handleBlockBreakAction the way a real click does and then waits to see the block go.
        FTGameTests.test("the_real_break_path_triggers_a_swap", 80, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            BlockPos target = helper.absolutePos(TARGET);

            // SURVIVAL, EXPLICITLY. makeMockServerPlayerInLevel comes up in CREATIVE, where
            // START_DESTROY_BLOCK destroys the block instantly whatever is in your hand. The first
            // version of this test omitted it and passed with the swap entirely disabled, which is
            // the exact shape of the tautology this suite keeps catching: green, and about nothing.
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));
            player.snapTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 2.5, 0F, 0F);

            player.gameMode.handleBlockBreakAction(target,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
                player.level().getMaxY(), 0);

            // ASSERTS THE OUTCOME, NOT THE MECHANISM, which is what makes this test survive the
            // hook moving. The timing is the proof: a netherite pickaxe clears stone in about five
            // ticks, and cobblestone against stone needs roughly a hundred and fifty. If the block
            // is gone inside forty, the swap happened and the dig ran to completion afterwards.
            // Both halves matter, and neither is observable from the swap code itself.
            // Asserted a tick later, because BreakSpeed fires from the game mode's tick loop rather
            // than synchronously inside handleBlockBreakAction.
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "vanilla's own break path should have triggered a swap");
                helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                    "and the pickaxe should be the thing in hand, found "
                        + player.getMainHandItem().getItem());
                ToolSwapper.swapOut(player);
                helper.succeed();
            });
        });

        // A DIG THAT DID NOT SWAP MUST NOT POISON THAT BLOCK FOR THE REST OF THE SESSION.
        //
        // Found in a real client, not here. The first version remembered the last block position
        // per player and swapped only when it changed, and cleared that memory in swapOut - which
        // returns early when no swap is active. So any dig that did not swap left the position
        // latched forever, and every later dig on it silently refused to swap. Mining a block with
        // the right tool already in hand is exactly that case, and it is the common one.
        //
        // The scenario is the one that failed: dig with a tie in hand (no swap), stop, then dig the
        // same block again with something useless. The second dig must swap.
        FTGameTests.test("a_dig_that_did_not_swap_does_not_latch_the_position", 80, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            BlockPos target = helper.absolutePos(TARGET);
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 2.5, 0F, 0F);

            // A TIE: the same pickaxe in hand as in the slot, so bestSlotFor returns -1 and no swap
            // happens. The point of the test is that this still records the position.
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.NETHERITE_PICKAXE));
            player.gameMode.handleBlockBreakAction(target,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
                player.level().getMaxY(), 0);

            helper.runAfterDelay(5, () -> {
                player.gameMode.handleBlockBreakAction(target,
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP,
                    player.level().getMaxY(), 1);
                helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "a tie should not have swapped anything in");

                // Second dig, same block, nothing useful in hand. Delayed past the gap that tells
                // one dig from the next.
                helper.runAfterDelay(15, () -> {
                    player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                        new ItemStack(Items.COBBLESTONE, 1));
                    player.gameMode.handleBlockBreakAction(target,
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
                        player.level().getMaxY(), 2);

                    helper.runAfterDelay(20, () -> {
                        helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                            "digging the same block again after stopping should swap");
                        helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                            "and the pickaxe should be in hand, found "
                                + player.getMainHandItem().getItem());
                        ToolSwapper.swapOut(player);
                        helper.succeed();
                    });
                });
            });
        });


        // THE PLAYER'S OWN SWITCH, which is a different question from the pack's. Bound to a key
        // because the moment you want it is while standing in front of the block that just swapped a
        // tool you did not want - a setting that needs a file edit will not get changed.
        FTGameTests.test("the_key_turns_the_swap_off_and_on_for_this_player", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));
            BlockPos target = helper.absolutePos(TARGET);
            player.snapTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 2.5, 0F, 0F);

            helper.assertTrue(ToolSwapper.swapping(player), "premise: on by default");
            helper.assertFalse(ToolSwapper.toggleWanted(player), "one press should turn it off");
            helper.assertFalse(ToolSwapper.swapping(player), "and it should read as off");

            // Off means the real break path does nothing, not merely that a flag changed.
            player.gameMode.handleBlockBreakAction(target,
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
                player.level().getMaxY(), 0);
            helper.runAfterDelay(20, () -> {
                helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "nothing should have swapped in with the player's own switch off");
                helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                    "and they should still hold their own item, found "
                        + player.getMainHandItem().getItem());

                helper.assertTrue(ToolSwapper.toggleWanted(player), "a second press turns it back on");
                helper.assertTrue(ToolSwapper.swapping(player), "and it should read as on again");
                helper.succeed();
            });
        });

        // TURNING IT OFF MID-SWING RETURNS THE ITEM, the same invariant the config gate has and for
        // the same reason: while a swap is live the player's own stack exists only in the attachment.
        FTGameTests.test("pressing_the_key_mid_swing_returns_the_item", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            ToolSwapper.swapIn(player, Blocks.STONE.defaultBlockState());
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "premise: the swap should be live before the key is pressed");

            ToolSwapper.toggleWanted(player);

            helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                "the live swap should have been unwound");
            helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                "and the player's own item should be back, found "
                    + player.getMainHandItem().getItem());
            helper.assertTrue(countEverywhere(player, Items.NETHERITE_PICKAXE) == 1,
                "with exactly one pickaxe still in existence, found "
                    + countEverywhere(player, Items.NETHERITE_PICKAXE));
            // Leave the switch as it was found; this class shares an environment with its siblings.
            ToolSwapper.toggleWanted(player);
            helper.succeed();
        });

    }
}
