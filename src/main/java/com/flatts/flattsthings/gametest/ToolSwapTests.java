package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSwapper;
import com.flatts.flattsthings.registry.FTAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
            // hook moving: vanilla's own break path was driven, and a pickaxe ended up in the hand.
            //
            // THE COMMENT HERE USED TO CLAIM MORE THAN THE TEST DOES, and the correction is the
            // point. It said the block being gone inside forty ticks proved the dig also ran to
            // completion. The block is never gone - probed, and it is still stone at tick twenty.
            //
            // The mechanism was then explained wrongly TWICE, which is worth more than the fix. The
            // real reason is not that the game mode never ticks: ServerPlayer.tick calls
            // gameMode.tick every tick, and the level ticks this player. It is that
            // ServerPlayerGameMode.tick only ever calls incrementDestroyProgress while
            // isDestroyingBlock; the call that actually removes the block needs hasDelayedDestroy,
            // which STOP_DESTROY_BLOCK sets, or an insta-mine. START alone digs forever.
            //
            // So "the swap does not reset destroy progress every tick" is NOT pinned here and is not
            // pinned anywhere. It is the reason the hook is shaped this way and it remains a
            // client-side observation. Left as a known gap rather than a sentence that reads as
            // covered.
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


        // MOVING FROM ONE BLOCK TO ANOTHER MID-HOLD MUST RE-PICK THE TOOL. Reported from real play:
        // start breaking a log, then swing onto dirt. The first version refused to swap while a swap
        // was live, so the axe stayed for the dirt - and worse, the dig record was written before the
        // swap rather than after, so an unwind cleared it and the stranded backstop took the tool
        // away forty ticks later, leaving the player digging bare-handed.
        FTGameTests.test("sweeping_onto_a_different_block_picks_the_right_tool", 60, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.OAK_LOG);
            BlockPos log = helper.absolutePos(TARGET);
            BlockPos dirt = helper.absolutePos(TARGET.above());
            helper.setBlock(TARGET.above(), Blocks.DIRT);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_AXE));
            ToolSlots.set(player, 1, new ItemStack(Items.NETHERITE_SHOVEL));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));
            player.snapTo(log.getX() + 0.5, log.getY() + 1.0, log.getZ() + 2.5, 0F, 0F);

            // Drive the real hook rather than swapIn, so the dig record is written the way play
            // writes it - that ordering is half the bug.
            player.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState(), log);
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_AXE),
                "the axe should come out for a log, found " + player.getMainHandItem().getItem());

            // Now the crosshair moves to dirt, still holding.
            player.getDestroySpeed(Blocks.DIRT.defaultBlockState(), dirt);
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_SHOVEL),
                "the shovel should come out for dirt, found " + player.getMainHandItem().getItem());

            // AND NOTHING WAS DUPLICATED OR LOST ON THE WAY. The axe went back to a slot, the
            // cobblestone is still recorded as the displaced item, and each exists exactly once.
            helper.assertTrue(countEverywhere(player, Items.NETHERITE_AXE) == 1,
                "exactly one axe, found " + countEverywhere(player, Items.NETHERITE_AXE));
            helper.assertTrue(countEverywhere(player, Items.NETHERITE_SHOVEL) == 1,
                "exactly one shovel, found " + countEverywhere(player, Items.NETHERITE_SHOVEL));

            ToolSwapper.swapOut(player);
            helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                "and the player's own item comes back at the end, found "
                    + player.getMainHandItem().getItem());
            helper.assertTrue(countEverywhere(player, Items.COBBLESTONE) == 1,
                "exactly one cobblestone, found " + countEverywhere(player, Items.COBBLESTONE));
            helper.succeed();
        });

        // THE DIG RECORD MUST SURVIVE A RE-SWAP, which is the half that made this a lost tool rather
        // than merely the wrong one. Asserted through the backstop itself: after moving blocks, a
        // player who is plainly still digging must not be unwound.
        FTGameTests.test("moving_between_blocks_does_not_strand_the_swap", 60, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.OAK_LOG);
            BlockPos log = helper.absolutePos(TARGET);
            BlockPos dirt = helper.absolutePos(TARGET.above());
            helper.setBlock(TARGET.above(), Blocks.DIRT);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_AXE));
            ToolSlots.set(player, 1, new ItemStack(Items.NETHERITE_SHOVEL));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            player.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState(), log);
            player.getDestroySpeed(Blocks.DIRT.defaultBlockState(), dirt);

            // One tick of the backstop. With the record cleared it unwinds here; with it written
            // after the swap it does not.
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));

            helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                "a player still digging must not be unwound by the stranded backstop");
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_SHOVEL),
                "and should still hold the shovel, found " + player.getMainHandItem().getItem());
            ToolSwapper.swapOut(player);
            helper.succeed();
        });


        // A BREAKSPEED WITH NO POSITION MUST NOT STRAND A LIVE SWAP. Vanilla's deprecated one-argument
        // getDestroySpeed passes a null position, and anything calling it while a player is mid-dig
        // would otherwise let the dig record go stale until the backstop fired and took the tool out
        // of their hand. The handler treats it as "still swinging" and refreshes the record instead;
        // coverage showed that whole branch had never run.
        FTGameTests.test("a_break_speed_with_no_position_keeps_the_swap", 60, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            BlockPos target = helper.absolutePos(TARGET);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            player.getDestroySpeed(Blocks.STONE.defaultBlockState(), target);
            helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                "premise: the swap should be live");

            // THE ELAPSED TIME IS THE TEST, and the first version of this did not have it. It called
            // the positionless overload once and asserted the swap was still live, which nothing on
            // that path could have changed - deleting the computeIfPresent under test left it green.
            // Covering the line without pinning the behaviour is the exact green this suite keeps
            // catching elsewhere.
            //
            // So: let more than the backstop's window pass with ONLY positionless calls, which is a
            // player still swinging as far as the mod can tell, and then tick. If the record is not
            // being refreshed, the backstop unwinds and this fails.
            helper.runAfterDelay(10, () -> {
                // The positionless overload, which is what a mod or a vanilla path without a block
                // reference calls. Deprecated in 26.1 and still reachable.
                player.getDestroySpeed(Blocks.STONE.defaultBlockState());
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));

                helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "a positionless BreakSpeed must keep the dig record alive; the backstop took"
                        + " the tool out of a player who was still swinging");
                helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                    "and the pickaxe should still be in hand, found "
                        + player.getMainHandItem().getItem());
                ToolSwapper.swapOut(player);
                helper.succeed();
            });
        });

        // A LIVE SWAP WITH NO DIG ON RECORD IS STRANDED BY DEFINITION, and unwinding it is the only
        // thing standing between a crash mid-swing and a player's item disappearing. swapIn leaves no
        // record, so this reaches the branch the ordinary paths never do.
        FTGameTests.test("a_swap_with_no_dig_on_record_is_unwound", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            ToolSwapper.swapIn(player, Blocks.STONE.defaultBlockState());
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "premise: the swap is live and left no dig on record");

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));

            helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                "a swap with no dig recorded must be unwound rather than left holding the tool");
            helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                "and the player's own item comes back, found " + player.getMainHandItem().getItem());
            helper.assertTrue(countEverywhere(player, Items.NETHERITE_PICKAXE) == 1,
                "with exactly one pickaxe, found " + countEverywhere(player, Items.NETHERITE_PICKAXE));
            helper.succeed();
        });

        // A NULL PLAYER IS HARMLESS, and the reason first written here was wrong. It said
        // BreakBlockEvent can carry a null player from an explosion; it cannot. That event is built
        // only in CommonHooks.fireBlockBreak, which dereferences the player before constructing it,
        // and this mod no longer listens to it anyway.
        //
        // The guard stays because swapOut is public and is called from the config gate, the key
        // handler and the tick handler, and because a null-safe unwind costs one comparison. Kept
        // with an honest reason rather than deleted with a wrong one.
        FTGameTests.test("unwinding_a_null_player_is_harmless", 20, helper -> {
            ToolSwapper.swapOut(null);
            helper.succeed();
        });


        // BREAKING A BLOCK KEEPS THE TOOL, so the next block gets it too. This test pinned the
        // opposite until a review found that the BreakBlockEvent handler it was pinning ate the
        // drops (see the test below, and the long note in ToolSwapper). With that handler gone, the
        // rule is one rule: the tool goes back five ticks after you STOP digging.
        //
        // The half worth pinning is the first half. If the swap ended the instant a block broke,
        // chain-mining would put the player's own item back in their hand between every pair of
        // blocks, and the next dig would start with the wrong thing held.
        FTGameTests.test("breaking_a_block_keeps_the_tool_for_the_next_one", 60, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            BlockPos target = helper.absolutePos(TARGET);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            player.getDestroySpeed(stone, target);
            helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                "premise: the dig should have swapped a pickaxe in");

            player.gameMode.destroyBlock(target);

            helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                "the swap must survive the block breaking, or chain-mining hands the player their"
                    + " own item back between every block");
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "with the pickaxe still in hand, found " + player.getMainHandItem().getItem());

            // And it still ends, by the one rule that ends it.
            helper.runAfterDelay(10, () -> {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "and the backstop should still put it away once digging stops");
                helper.assertTrue(countEverywhere(player, Items.NETHERITE_PICKAXE) == 1,
                    "with exactly one pickaxe, found "
                        + countEverywhere(player, Items.NETHERITE_PICKAXE));
                helper.succeed();
            });
        });

        // THE SWAP MUST NOT EAT THE BLOCK'S DROPS, which is what it did until a review of the test
        // above asked what else BreakBlockEvent is next to.
        //
        // NeoForge posts that event from CommonHooks.fireBlockBreak, which ServerPlayerGameMode
        // .destroyBlock calls on its FIRST line - before it reads getMainHandItem, before
        // canHarvestBlock decides whether the block drops anything, and before mineBlock applies
        // durability. Unwinding the swap there put the player's own item back in their hand and then
        // let vanilla ask that item whether it could harvest stone. It could not. So the mod swapped
        // a pickaxe in, mined the stone with it, and dropped nothing, while the pickaxe took no wear.
        //
        // Every earlier test in this suite missed it because none of them finished a block: this is
        // the first one that breaks anything, so it is the first that could see the drops.
        FTGameTests.test("breaking_a_block_still_drops_it_and_wears_the_tool", 60, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            BlockPos target = helper.absolutePos(TARGET);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            // DIRT rather than cobblestone, so the drop under test cannot be confused with the item
            // the player was already carrying.
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.DIRT, 1));

            player.getDestroySpeed(stone, target);
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "premise: the dig should have swapped a pickaxe in");

            player.gameMode.destroyBlock(target);

            helper.assertFalse(
                player.level().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(target).inflate(4.0),
                    entity -> entity.getItem().is(Items.COBBLESTONE)).isEmpty(),
                "stone mined with a swapped-in pickaxe must drop cobblestone; the swap unwound"
                    + " before vanilla checked what was in the player's hand");

            // The other half of the same bug: the tool that did the work has to be the one that wears
            // out. A pickaxe that mines for free is a pickaxe that never breaks.
            //
            // Read after the backstop, because with the BreakBlockEvent handler gone that is what
            // ends the swap. Ten ticks and a hand-posted tick event, for the reason spelled out on
            // the backstop test below: the level ticks this player but nothing calls doTick on it.
            helper.runAfterDelay(10, () -> {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                ItemStack pickaxe = ToolSlots.get(player, 0).is(Items.NETHERITE_PICKAXE)
                    ? ToolSlots.get(player, 0)
                    : player.getMainHandItem();
                helper.assertTrue(pickaxe.is(Items.NETHERITE_PICKAXE),
                    "the pickaxe should have come back, found " + pickaxe.getItem());
                helper.assertTrue(pickaxe.getDamageValue() > 0,
                    "and it should have taken durability for the block it broke, damage was "
                        + pickaxe.getDamageValue());
                helper.assertTrue(player.getMainHandItem().is(Items.DIRT),
                    "with the player's own item back in hand, found "
                        + player.getMainHandItem().getItem());
                helper.succeed();
            });
        });

        // THE STRANDED BACKSTOP IS THE ONLY UNWIND FOR LETTING GO OF THE MOUSE, because the events
        // that would say so are client-only in 26.1. It had no test at all: coverage showed the
        // branch that fires it never went true, which means the five-tick timeout - the thing
        // standing between a player who tapped a block and a pickaxe they never chose - was
        // load-bearing and unproven.
        //
        // Time is real here rather than simulated. tickCount advances for a mock player, because the
        // LEVEL ticks the entity, even though PlayerTickEvent.Post does not fire for one - that
        // comes from ServerGamePacketListenerImpl#tick and this player has no connection. So the
        // elapsed count is genuine and only the delivery is by hand, which is the same split
        // ConfigGateTests sets out at length for the mid-swing test.
        FTGameTests.test("letting_go_puts_the_tool_away_after_the_backstop", 60, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
            BlockPos target = helper.absolutePos(TARGET);
            player.setGameMode(GameType.SURVIVAL);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            player.getDestroySpeed(stone, target);
            helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                "premise: the dig should have swapped a pickaxe in");

            // One tick in, well inside the timeout, the swap must still be there - otherwise this
            // test would pass just as green against a backstop that fires immediately, which is the
            // bug that makes mining unusable rather than the one it guards against.
            helper.runAfterDelay(1, () -> {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "a swap must survive the tick right after a dig");

                helper.runAfterDelay(15, () -> {
                    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                    helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                        "a dig that stopped should have been unwound by the backstop");
                    helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                        "and the player's own item put back, found "
                            + player.getMainHandItem().getItem());
                    helper.assertTrue(countEverywhere(player, Items.NETHERITE_PICKAXE) == 1,
                        "with exactly one pickaxe, found "
                            + countEverywhere(player, Items.NETHERITE_PICKAXE));
                    helper.succeed();
                });
            });
        });

        // A TOOL WITH NOWHERE TO GO IS DROPPED, NEVER DELETED. This is the last branch of swapOut
        // and the only one whose failure is silent: the slot it came from is taken, every inventory
        // slot is full, and the alternative to dropping is the item ceasing to exist while the
        // player watches their own stack come back. Nothing exercised it.
        FTGameTests.test("a_tool_with_nowhere_to_go_is_dropped_not_eaten", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            // TWO THINGS HAD TO BE ARRANGED BEFORE THIS TEST COULD SEE ANYTHING, and both looked
            // at first like the mod losing the item.
            //
            // SURVIVAL, EXPLICITLY. Inventory.add returns TRUE for a creative player whatever the
            // state of the inventory - hasInfiniteMaterials sets the stack to zero and reports
            // success - so swapOut took the "it went in the inventory" branch, the pickaxe ceased to
            // exist, and the drop under test never ran. A full inventory is not a thing a creative
            // player can have. That is the third time the creative mock player has hidden a check in
            // this repo.
            //
            // AND THE PLAYER HAS TO BE MOVED INTO THE PLOT. makeMockServerPlayerInLevel leaves the
            // player at the world ORIGIN, not in the test structure, so a dropped item lands in a
            // chunk nothing has loaded and getEntitiesOfClass does not index it. The item existed
            // the whole time - drop returned it - and the query answered empty, which reads exactly
            // like the deletion this test is here to rule out.
            player.setGameMode(GameType.SURVIVAL);
            helper.setBlock(FLOOR, Blocks.STONE);
            BlockPos floor = helper.absolutePos(FLOOR);
            player.snapTo(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5, 0F, 0F);
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 1));

            ToolSwapper.swapIn(player, stone);
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "premise: the pickaxe should be in hand");

            // Both ways home are closed while the tool is out: something else takes the slot it came
            // from, and every inventory slot is occupied.
            ToolSlots.set(player, 0, new ItemStack(Items.IRON_AXE));
            for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
                if (index != player.getInventory().getSelectedSlot()) {
                    player.getInventory().setItem(index, new ItemStack(Items.DIRT, 64));
                }
            }

            ToolSwapper.swapOut(player);

            helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                "the player's own item still comes back first, found "
                    + player.getMainHandItem().getItem());
            helper.assertTrue(countEverywhere(player, Items.NETHERITE_PICKAXE) == 0,
                "the pickaxe cannot have gone back into a full inventory");
            helper.assertFalse(
                player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(8.0),
                    entity -> entity.getItem().is(Items.NETHERITE_PICKAXE)).isEmpty(),
                "so it must be on the ground beside the player, and it is nowhere");
            helper.succeed();
        });

        // A TOOL THAT BROKE WHILE IT WAS OUT COSTS THE PLAYER NOTHING ELSE. Vanilla empties the hand
        // when the last point of durability goes, so a swap can be unwound with nothing to put back;
        // what must still happen is that the player's own item returns and the slot is left in a
        // state they can use.
        //
        // THIS DOES NOT PIN THE `inHand.isEmpty()` GUARD IN swapOut, and the difference was measured
        // rather than assumed: deleting that guard leaves this test green. An empty stack fails
        // ToolSlots.isValid, Inventory.add refuses it, and drop returns null for it, so every path
        // below the guard is already a no-op on nothing. The guard is an early exit, not a
        // behaviour, and no test can tell it from its own absence. Coverage counts the line either
        // way, which is exactly the sort of green worth distrusting.
        FTGameTests.test("a_tool_that_broke_while_out_costs_nothing_else", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.WOODEN_PICKAXE));
            player.getInventory().setItem(player.getInventory().getSelectedSlot(),
                new ItemStack(Items.COBBLESTONE, 3));

            ToolSwapper.swapIn(player, stone);
            helper.assertTrue(player.getMainHandItem().is(Items.WOODEN_PICKAXE),
                "premise: the pickaxe should be in hand");

            // What vanilla leaves behind when the last point of durability goes.
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), ItemStack.EMPTY);

            ToolSwapper.swapOut(player);

            helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                "the player's own item comes back regardless, found "
                    + player.getMainHandItem().getItem());
            helper.assertTrue(ToolSlots.get(player, 0).isEmpty(),
                "and the slot the broken tool came from stays empty, found "
                    + ToolSlots.get(player, 0));
            helper.succeed();
        });

    }
}
