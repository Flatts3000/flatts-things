package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSwapper;
import com.flatts.flattsthings.registry.FTAttachments;
import net.minecraft.core.BlockPos;
import com.flatts.flattsthings.registry.FTCreativeTabs;
import com.flatts.flattsthings.registry.FTItems;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The config switches, flipped.
 *
 * <p><b>A switch that is never flipped in a test is a switch nobody has checked.</b> Every other
 * test in this suite runs with every feature on, which is exactly the state in which a gate reading
 * the wrong feature, or sitting on a path that never runs, passes everything. These turn one off and
 * assert the behaviour actually stops.
 *
 * <p>The config is loaded here, unlike in the JUnit layer, because a GameTest runs inside a real
 * server. That is the whole reason these are GameTests rather than unit tests: {@code FTConfig}
 * answers with the shipped default when nothing is loaded, so a unit test cannot tell a working
 * switch from an ignored one.
 *
 * <p>Every test restores what it changed in a {@code finally}. A leaked {@code false} would not fail
 * here - it would fail somewhere else, in a test that looks unrelated.
 */
final class ConfigGateTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos TARGET = new BlockPos(1, 1, 1);

    private ConfigGateTests() {
    }

    /** A survival player standing over a stone block, with a pickaxe stored and junk in hand. */
    @SuppressWarnings("removal")
    private static ServerPlayer digger(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(TARGET, Blocks.STONE);
        player.setGameMode(GameType.SURVIVAL);
        ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
        player.getInventory().setItem(player.getInventory().getSelectedSlot(),
            new ItemStack(Items.COBBLESTONE, 1));
        BlockPos target = helper.absolutePos(TARGET);
        player.snapTo(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 2.5, 0F, 0F);
        return player;
    }

    private static void startDigging(ServerPlayer player, BlockPos target) {
        player.gameMode.handleBlockBreakAction(target,
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
            player.level().getMaxY(), 0);
    }

    static void register() {
        BlockState stone = Blocks.STONE.defaultBlockState();

        FTGameTests.test("the_swap_does_nothing_when_it_is_switched_off", 80, FTGameTests.aloneIn("the_swap_does_nothing_when_it_is_switched_off"), helper -> {
            ServerPlayer player = digger(helper);
            FTConfig.switchFor(FTConfig.TOOL_AUTO_SWAP).set(false);
            startDigging(player, helper.absolutePos(TARGET));

            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                        "the switch is off, so nothing should have swapped in");
                    helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                        "and the player should still be holding their own item, found "
                            + player.getMainHandItem().getItem());
                    helper.assertTrue(ToolSlots.get(player, 0).is(Items.NETHERITE_PICKAXE),
                        "with the pickaxe left in its slot");
                } finally {
                    FTConfig.switchFor(FTConfig.TOOL_AUTO_SWAP).set(true);
                }
                helper.succeed();
            });
        });

        // THE SLOTS ARE WHERE THE SWAP TAKES FROM, so turning them off has to turn the swap off with
        // them whatever the swap's own switch says. Asserted rather than assumed, because the
        // dependency lives in one method and a caller reading the wrong switch would look correct.
        FTGameTests.test("switching_the_slots_off_switches_the_swap_off_with_them", 80, FTGameTests.aloneIn("switching_the_slots_off_switches_the_swap_off_with_them"), helper -> {
            ServerPlayer player = digger(helper);
            FTConfig.switchFor(FTConfig.TOOL_SLOTS).set(false);
            startDigging(player, helper.absolutePos(TARGET));

            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertFalse(FTConfig.toolAutoSwap(),
                        "the swap should read as off while the slots are off");
                    helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                        "and nothing should have swapped in");
                } finally {
                    FTConfig.switchFor(FTConfig.TOOL_SLOTS).set(true);
                }
                helper.succeed();
            });
        });

        // SWITCHED OFF MID-SWING MUST GIVE THE ITEM BACK. While a swap is live the player's own
        // stack exists in exactly one place - the attachment - so a gate that only stopped NEW swaps
        // would strand it the moment somebody edited the config, and the item would be gone with no
        // way to work out where. This is the accounting invariant the rest of the swap suite pins,
        // applied to the one event that can happen between swapIn and swapOut.
        //
        // POSTS THE TICK EVENT BY HAND, which this suite otherwise refuses to do, and the reason it
        // is right here and wrong elsewhere is worth being exact about. The LeftClickBlock bug hid
        // behind a hand-posted event because the question WAS whether anything posts it server-side,
        // and posting it answered that question with the test's own assumption. Here that question
        // is already answered, in 26.1's own source: PlayerTickEvent.Post is fired for a ServerPlayer
        // from ServerGamePacketListenerImpl#tick, once per tick, for every connected player. What
        // cannot be arranged is a CONNECTED player - makeMockServerPlayerInLevel builds one with no
        // connection, so it is never ticked, and driving "the game's own entry point" would be
        // waiting for a tick that is structurally never coming. The handler's logic is the unit
        // under test; its delivery is vanilla's job and was read rather than assumed.
        FTGameTests.test("switching_the_swap_off_mid_swing_returns_the_item", 80, FTGameTests.aloneIn("switching_the_swap_off_mid_swing_returns_the_item"), helper -> {
            ServerPlayer player = digger(helper);
            BlockPos target = helper.absolutePos(TARGET);

            // SWAPS IN THROUGH THE REAL HOOK rather than by calling swapIn, and the difference is
            // the whole test. getDestroySpeed is what vanilla calls while a block is being broken
            // and it posts BreakSpeed, so this leaves a dig on record the way an actual swing does.
            // Calling swapIn directly leaves none - and then the tick handler unwinds via its
            // "a live swap with no dig on record is stranded" branch, which passes with the config
            // gate deleted. Checked by deleting it: the test stayed green and proved nothing.
            player.getDestroySpeed(stone, target);
            helper.assertTrue(player.getMainHandItem().is(Items.NETHERITE_PICKAXE),
                "premise: the swap should be live before the switch is touched, found "
                    + player.getMainHandItem().getItem());
            helper.assertTrue(player.getData(FTAttachments.TOOL_SWAP).active(),
                "premise: and recorded as live");

            try {
                FTConfig.switchFor(FTConfig.TOOL_AUTO_SWAP).set(false);
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));

                helper.assertFalse(player.getData(FTAttachments.TOOL_SWAP).active(),
                    "the live swap should have been unwound");
                helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE),
                    "and the player's own item should be back in hand, found "
                        + player.getMainHandItem().getItem());
                helper.assertTrue(ToolSlots.get(player, 0).is(Items.NETHERITE_PICKAXE),
                    "with the pickaxe back in its slot");
            } finally {
                FTConfig.switchFor(FTConfig.TOOL_AUTO_SWAP).set(true);
            }
            helper.succeed();
        });

        // BUILDS THE TAB, rather than asserting the config reads back what it was just set to -
        // which would be a test of the config class with the creative tab's name on it. Building it
        // is what proves the displayItems callback consults the switch at all.
        FTGameTests.test("the_plates_leave_the_creative_tab_when_switched_off", 20, FTGameTests.aloneIn("the_plates_leave_the_creative_tab_when_switched_off"), helper -> {
            CreativeModeTab tab = FTCreativeTabs.FLATTS_THINGS_TAB.get();
            CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess());
            try {
                FTConfig.switchFor(FTConfig.PLAYER_PRESSURE_PLATES).set(false);
                tab.buildContents(parameters);
                helper.assertTrue(tab.getDisplayItems().isEmpty(),
                    "the tab should be empty with the plates switched off, found "
                        + tab.getDisplayItems().size() + " item(s)");
            } finally {
                FTConfig.switchFor(FTConfig.PLAYER_PRESSURE_PLATES).set(true);
                tab.buildContents(parameters);
            }
            helper.assertTrue(tab.getDisplayItems().size() == FTItems.PLATES.size(),
                "and hold every plate again once restored, or every later test is running on a lie");
            helper.succeed();
        });

    }
}
