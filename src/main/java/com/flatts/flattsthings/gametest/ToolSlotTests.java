package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.menu.ToolSlotsMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tool slot storage: that it holds things, refuses the wrong things, and does not lose them.
 *
 * <p>Slice one of issue #24. No screen and no auto-swap yet; this is the layer both of those sit on,
 * and it is the layer where a mistake is invisible until somebody loses their gear.
 */
final class ToolSlotTests {

    private ToolSlotTests() {
    }

    private static void report(GameTestHelper helper, List<String> problems, String what) {
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " " + what + ": " + String.join(", ", problems));
        }
        helper.succeed();
    }

    static void register() {
        FTGameTests.test("tool_slots_start_empty", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(ToolSlots.of(player).isEmpty(),
                "a fresh player must have empty tool slots");
            helper.succeed();
        });

        FTGameTests.test("a_tool_put_in_a_slot_stays_there", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.DIAMOND_PICKAXE));
            ItemStack held = ToolSlots.get(player, 0);
            helper.assertTrue(held.is(Items.DIAMOND_PICKAXE),
                "slot 0 should hold the pickaxe, holds " + held.getItem());
            helper.assertFalse(ToolSlots.of(player).isEmpty(), "the slots are no longer empty");
            helper.succeed();
        });

        // THE ONE THAT MATTERS. copyOnDeath is opt-in on NeoForge attachments, so without the flag
        // every tool a player owns is destroyed the first time they fall in lava - and that reads as
        // the mod eating your gear rather than as a missing flag, so it would not be reported.
        // restoreFrom(dead, false) is what a death is; true would be keepInventory.
        FTGameTests.test("tool_slots_survive_death", 20, helper -> {
            ServerPlayer dead = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(dead, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            ToolSlots.set(dead, 2, new ItemStack(Items.SHEARS));

            ServerPlayer respawned = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(ToolSlots.of(respawned).isEmpty(),
                "precondition: a new player starts with empty slots");
            respawned.restoreFrom(dead, false);

            helper.assertTrue(ToolSlots.get(respawned, 0).is(Items.NETHERITE_PICKAXE),
                "the pickaxe did not survive death");
            helper.assertTrue(ToolSlots.get(respawned, 2).is(Items.SHEARS),
                "the shears did not survive death, so only some slots are copied");
            helper.succeed();
        });

        FTGameTests.test("two_players_have_two_sets_of_slots", 20, helper -> {
            ServerPlayer first = helper.makeMockServerPlayerInLevel();
            ServerPlayer second = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(first, 0, new ItemStack(Items.IRON_AXE));

            helper.assertTrue(ToolSlots.of(second).isEmpty(),
                "one player's tools must not appear in another's slots");
            helper.assertTrue(ToolSlots.get(first, 0).is(Items.IRON_AXE),
                "the first player kept their own axe");
            helper.succeed();
        });

        // The tag is the compatibility surface: another mod's pickaxe carries #minecraft:pickaxes
        // already, so it fits with no knowledge of it here. Walking the vanilla set proves the tag
        // file actually resolves, which a hardcoded class check would never have needed and which
        // silently fails if the JSON is wrong.
        FTGameTests.test("the_tool_slot_tag_accepts_every_vanilla_tool_family", 20, helper -> {
            List<String> rejected = new ArrayList<>();
            for (var item : List.of(Items.WOODEN_PICKAXE, Items.NETHERITE_PICKAXE, Items.DIAMOND_AXE,
                    Items.STONE_SHOVEL, Items.GOLDEN_HOE, Items.IRON_SWORD, Items.SHEARS)) {
                if (!ToolSlots.isValid(new ItemStack(item))) {
                    rejected.add(item.toString());
                }
            }
            report(helper, rejected, "vanilla tools the slots refuse");
        });

        FTGameTests.test("a_slot_refuses_something_that_is_not_a_tool", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.assertFalse(ToolSlots.isValid(new ItemStack(Items.COBBLESTONE)),
                "cobblestone is not a tool and must not be valid");
            boolean threw = false;
            try {
                ToolSlots.set(player, 0, new ItemStack(Items.COBBLESTONE));
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "putting cobblestone in a tool slot must be refused loudly");
            helper.assertTrue(ToolSlots.of(player).isEmpty(), "the refused item must not have landed");
            helper.succeed();
        });

        // ---------------- the menu (slice two) ----------------

        FTGameTests.test("the_menu_exposes_the_slots_plus_the_player_inventory", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlotsMenu menu = new ToolSlotsMenu(1, player.getInventory());
            int expected = ToolSlots.SIZE + 36;
            helper.assertTrue(menu.slots.size() == expected,
                "expected " + expected + " slots, got " + menu.slots.size());
            helper.succeed();
        });

        // mayPlace is what actually guards a tool slot during play. The container deliberately does
        // not re-check, so if this were wrong nothing else would stop cobblestone going in.
        FTGameTests.test("a_tool_slot_refuses_a_non_tool_through_the_menu", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlotsMenu menu = new ToolSlotsMenu(1, player.getInventory());
            helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(Items.DIAMOND_PICKAXE)),
                "a pickaxe must be allowed in a tool slot");
            helper.assertFalse(menu.slots.get(0).mayPlace(new ItemStack(Items.COBBLESTONE)),
                "cobblestone must be refused by the slot itself");
            helper.succeed();
        });

        FTGameTests.test("a_tool_slot_holds_only_one", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlotsMenu menu = new ToolSlotsMenu(1, player.getInventory());
            helper.assertTrue(menu.slots.get(0).getMaxStackSize() == 1,
                "a tool slot holds one, got " + menu.slots.get(0).getMaxStackSize());
            helper.succeed();
        });

        // Shift-clicking a tool out of the inventory should land it in a tool slot, and the change
        // must reach the attachment rather than only the menu's working copy.
        FTGameTests.test("shift_clicking_a_tool_moves_it_into_a_slot", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_AXE));
            ToolSlotsMenu menu = new ToolSlotsMenu(1, player.getInventory());

            int hotbarSlot = ToolSlots.SIZE + 27;
            menu.quickMoveStack(player, hotbarSlot);

            helper.assertTrue(ToolSlots.get(player, 0).is(Items.DIAMOND_AXE),
                "the axe should now be in tool slot 0, found " + ToolSlots.get(player, 0).getItem());
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "the axe should have left the hotbar");
            helper.succeed();
        });

        FTGameTests.test("shift_clicking_a_tool_out_returns_it_to_the_inventory", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.IRON_HOE));
            ToolSlotsMenu menu = new ToolSlotsMenu(1, player.getInventory());

            menu.quickMoveStack(player, 0);

            helper.assertTrue(ToolSlots.get(player, 0).isEmpty(),
                "the hoe should have left the tool slot");
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_HOE)),
                "the hoe should be back in the inventory");
            helper.succeed();
        });

        // An empty stack has to be valid or there is no way to take a tool back out.
        FTGameTests.test("a_slot_can_be_emptied", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 1, new ItemStack(Items.IRON_SHOVEL));
            ItemStack previous = ToolSlots.set(player, 1, ItemStack.EMPTY);
            helper.assertTrue(previous.is(Items.IRON_SHOVEL),
                "clearing a slot returns what was in it, got " + previous.getItem());
            helper.assertTrue(ToolSlots.of(player).isEmpty(), "the slot is empty afterwards");
            helper.succeed();
        });
    }
}
