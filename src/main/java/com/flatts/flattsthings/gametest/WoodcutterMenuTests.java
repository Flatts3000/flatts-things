package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.woodcutter.WoodcutterMenu;
import com.flatts.flattsthings.registry.FTBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * The woodcutter's menu: what it offers, what it charges, and what it refuses.
 *
 * <p>Driven through the menu itself rather than through the screen, because the screen is client
 * code and no headless test reaches a line of it. Everything a player can do here goes through
 * {@code clickMenuButton} and the result slot's {@code onTake}, which is exactly what these call.
 *
 * <p><b>The options are asserted through {@code visibleOptions}</b>, which reads the synced display
 * slots - the same thing the screen reads. Asserting the private recipe list instead would prove the
 * server found recipes while proving nothing about whether a client could ever see them.
 */
final class WoodcutterMenuTests {

    private static final BlockPos CUTTER = new BlockPos(1, 1, 1);

    private WoodcutterMenuTests() {
    }

    private static WoodcutterMenu openWith(net.minecraft.gametest.framework.GameTestHelper helper,
                                           ServerPlayer player, ItemStack input) {
        helper.setBlock(CUTTER, FTBlocks.WOODCUTTER.get());
        WoodcutterMenu menu = new WoodcutterMenu(1, player.getInventory(),
            ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(CUTTER)));
        menu.inputContainer.setItem(0, input);
        return menu;
    }

    private static ServerPlayer survivalPlayer(
            net.minecraft.gametest.framework.GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // SURVIVAL, because a creative player's Inventory.add reports success and stores nothing,
        // and every count in this file would then read zero for a reason unrelated to woodcutting.
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    static void register() {
        // PLANKS IN, TWO SHAPES OFFERED. This is the feature: the block reads this mod's own recipe
        // type and puts the results where a client can see them.
        FTGameTests.test("a_woodcutter_offers_the_shapes_for_its_input", 20, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            WoodcutterMenu menu = openWith(helper, player, new ItemStack(Items.OAK_PLANKS, 8));

            List<ItemStack> offered = menu.visibleOptions();
            helper.assertTrue(offered.size() == 2,
                "oak planks should offer a stair and a slab, offered " + offered.size());
            helper.assertTrue(offered.stream().anyMatch(o -> o.is(Items.OAK_STAIRS)),
                "one of them should be stairs");
            helper.assertTrue(offered.stream().anyMatch(o -> o.is(Items.OAK_SLAB)),
                "and one should be a slab");
            helper.assertTrue(menu.hasInput(), "and the menu should know it has something to cut");
            helper.succeed();
        });

        // CHOOSING ONE FILLS THE RESULT SLOT. Nothing is charged yet - looking at an option must not
        // cost a plank, which is why the input is only consumed when the result is taken.
        FTGameTests.test("choosing_a_shape_fills_the_result_slot", 20, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            WoodcutterMenu menu = openWith(helper, player, new ItemStack(Items.OAK_PLANKS, 8));

            int stairs = indexOf(menu, Items.OAK_STAIRS);
            helper.assertTrue(stairs >= 0, "premise: stairs are on offer");
            helper.assertTrue(menu.clickMenuButton(player, stairs), "the button should be accepted");

            helper.assertTrue(menu.getSlot(1).getItem().is(Items.OAK_STAIRS),
                "the result slot should hold stairs, held " + menu.getSlot(1).getItem().getItem());
            helper.assertTrue(menu.inputContainer.getItem(0).getCount() == 8,
                "and nothing should have been charged yet, input was "
                    + menu.inputContainer.getItem(0).getCount());
            helper.succeed();
        });

        // TAKING IT CHARGES EXACTLY ONE PLANK, and leaves the menu ready for the next one.
        FTGameTests.test("taking_the_result_charges_one_plank", 20, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            WoodcutterMenu menu = openWith(helper, player, new ItemStack(Items.OAK_PLANKS, 8));
            int slab = indexOf(menu, Items.OAK_SLAB);
            menu.clickMenuButton(player, slab);

            ItemStack taken = menu.getSlot(1).getItem().copy();
            menu.getSlot(1).onTake(player, menu.getSlot(1).getItem());

            helper.assertTrue(taken.getCount() == 2,
                "a plank should cut into two slabs, cut " + taken.getCount());
            helper.assertTrue(menu.inputContainer.getItem(0).getCount() == 7,
                "exactly one plank should have been spent, left "
                    + menu.inputContainer.getItem(0).getCount());
            helper.assertTrue(menu.getSlot(1).getItem().is(Items.OAK_SLAB),
                "and the next one should already be waiting");
            helper.succeed();
        });

        // A BUTTON THAT NAMES NOTHING IS REFUSED. clickMenuButton is reachable from a client that
        // can send whatever it likes, so an index outside the offered list has to bounce rather than
        // index into an empty slot.
        FTGameTests.test("the_woodcutter_refuses_a_button_it_never_offered", 20, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            WoodcutterMenu menu = openWith(helper, player, new ItemStack(Items.OAK_PLANKS, 1));

            helper.assertFalse(menu.clickMenuButton(player, WoodcutterMenu.MAX_OPTIONS + 5),
                "an index past the end must be refused");
            helper.assertFalse(menu.clickMenuButton(player, -3), "and so must a negative one");
            helper.assertFalse(menu.clickMenuButton(player, WoodcutterMenu.MAX_OPTIONS - 1),
                "and an in-range index with nothing in it");
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(),
                "none of which should have produced a result");
            helper.succeed();
        });

        // CLOSING IT GIVES THE PLANKS BACK. This is the accounting invariant the rest of this repo
        // pins everywhere it can: an input slot is not storage, so whatever is sitting in it when
        // the screen closes has to come back to the player rather than quietly staying in a menu
        // that no longer exists. Nothing else here would notice the loss.
        FTGameTests.test("closing_the_woodcutter_gives_the_planks_back", 20, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            WoodcutterMenu menu = openWith(helper, player, new ItemStack(Items.OAK_PLANKS, 5));
            helper.assertTrue(countOf(player, Items.OAK_PLANKS) == 0,
                "premise: the planks are in the menu, not the inventory");

            menu.removed(player);

            helper.assertTrue(countOf(player, Items.OAK_PLANKS) == 5,
                "every plank should have come back, found " + countOf(player, Items.OAK_PLANKS));
            helper.assertTrue(menu.inputContainer.getItem(0).isEmpty(),
                "and the input slot should be empty");
            helper.succeed();
        });

        // AND IT CUTS ONLY WHAT IT HAS A RECIPE FOR. Without this, a menu that ignored its input
        // would pass everything above.
        FTGameTests.test("a_woodcutter_offers_nothing_for_stone", 20, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            WoodcutterMenu menu = openWith(helper, player, new ItemStack(Items.COBBLESTONE, 8));

            helper.assertTrue(menu.visibleOptions().isEmpty(),
                "cobblestone belongs in a stonecutter, and this offered "
                    + menu.visibleOptions().size() + " cuts");
            helper.assertFalse(menu.hasInput(), "and the menu should not think it has work to do");
            helper.succeed();
        });
    }

    private static int countOf(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Which offered option produces {@code item}, or -1. */
    private static int indexOf(WoodcutterMenu menu, net.minecraft.world.item.Item item) {
        List<ItemStack> offered = menu.visibleOptions();
        for (int index = 0; index < offered.size(); index++) {
            if (offered.get(index).is(item)) {
                return index;
            }
        }
        return -1;
    }
}
