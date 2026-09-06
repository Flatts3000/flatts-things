package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSlotsContainer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tool slot storage: that it holds things, refuses the wrong things, and does not lose them.
 *
 * <p>Slice one of issue #24. No screen and no auto-swap yet; this is the layer both of those sit on,
 * and it is the layer where a mistake is invisible until somebody loses their gear.
 */
final class ToolSlotTests {

    /**
     * Vanilla's own count: a crafting result, four crafting, four armour, twenty-seven inventory,
     * nine hotbar and the offhand. Ours are appended after it, so this is also the index of the
     * first one.
     */
    private static final int VANILLA_INVENTORY_SLOTS = 46;

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
                    Items.STONE_SHOVEL, Items.GOLDEN_HOE, Items.SHEARS)) {
                if (!ToolSlots.isValid(new ItemStack(item))) {
                    rejected.add(item.toString());
                }
            }
            report(helper, rejected, "vanilla tools the slots refuse");
        });

        // WEAPONS ARE NOT TOOLS, and this is the assertion that keeps it that way. Swords were in
        // the tag at first, which read as harmless - they are held in the hand and have a durability
        // bar like everything else here. They are not: these slots feed the auto-swap, so a sword in
        // one is a mod that puts a weapon in your hand while you are mining, and takes it away
        // again. Tools go here; fighting is the player's business.
        //
        // The tag is a pack-editable file, so this is a test of the shipped default rather than of
        // anything a pack cannot change.
        FTGameTests.test("a_weapon_does_not_belong_in_a_tool_slot", 20, helper -> {
            List<String> accepted = new ArrayList<>();
            for (var item : List.of(Items.WOODEN_SWORD, Items.IRON_SWORD, Items.NETHERITE_SWORD,
                    Items.TRIDENT, Items.BOW, Items.CROSSBOW, Items.MACE)) {
                if (ToolSlots.isValid(new ItemStack(item))) {
                    accepted.add(item.toString());
                }
            }
            report(helper, accepted, "weapons the tool slots wrongly accept");
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

        // mayPlace is what actually guards a tool slot during play. The container deliberately does
        // not re-check, so if this were wrong nothing else would stop cobblestone going in.
        FTGameTests.test("a_tool_slot_refuses_a_non_tool_through_the_menu", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AbstractContainerMenu menu = player.inventoryMenu;
            helper.assertTrue(menu.getSlot(VANILLA_INVENTORY_SLOTS).mayPlace(new ItemStack(Items.DIAMOND_PICKAXE)),
                "a pickaxe must be allowed in a tool slot");
            helper.assertFalse(menu.getSlot(VANILLA_INVENTORY_SLOTS).mayPlace(new ItemStack(Items.COBBLESTONE)),
                "cobblestone must be refused by the slot itself");
            helper.succeed();
        });

        FTGameTests.test("a_tool_slot_holds_only_one", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AbstractContainerMenu menu = player.inventoryMenu;
            helper.assertTrue(menu.getSlot(VANILLA_INVENTORY_SLOTS).getMaxStackSize() == 1,
                "a tool slot holds one, got " + menu.getSlot(VANILLA_INVENTORY_SLOTS).getMaxStackSize());
            helper.succeed();
        });

        // Shift-clicking a tool in the inventory should land it in a tool slot. Vanilla's own
        // quickMoveStack does not know these slots exist and would move the axe to the hotbar, so
        // this is the mixin's second injection rather than anything inherited.
        FTGameTests.test("shift_clicking_a_tool_moves_it_into_a_slot", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_AXE));
            AbstractContainerMenu menu = player.inventoryMenu;

            // Inventory slot 0 is the first hotbar slot, which is menu index 36 in vanilla numbering.
            menu.quickMoveStack(player, InventoryMenu.USE_ROW_SLOT_START);

            helper.assertTrue(ToolSlots.get(player, 0).is(Items.DIAMOND_AXE),
                "the axe should now be in tool slot 0, found " + ToolSlots.get(player, 0).getItem());
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                "the axe should have left the hotbar");
            helper.succeed();
        });

        // THE ACCOUNTING CHECK ON THAT PATH. quickMoveStack's return value drives a loop in doClick,
        // and getting it wrong duplicates the item rather than merely misbehaving - which is exactly
        // the failure a "did it arrive" assertion cannot see, because the axe did arrive.
        FTGameTests.test("shift_clicking_a_tool_into_a_slot_does_not_duplicate_it", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_AXE));
            AbstractContainerMenu menu = player.inventoryMenu;

            menu.quickMoveStack(player, InventoryMenu.USE_ROW_SLOT_START);
            // Again, the way doClick would: a second call must find nothing left to move.
            menu.quickMoveStack(player, InventoryMenu.USE_ROW_SLOT_START);

            helper.assertTrue(countEverywhere(player, Items.DIAMOND_AXE) == 1,
                "exactly one axe should exist, found " + countEverywhere(player, Items.DIAMOND_AXE));
            helper.succeed();
        });

        FTGameTests.test("shift_clicking_a_tool_out_returns_it_to_the_inventory", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlots.set(player, 0, new ItemStack(Items.IRON_HOE));
            AbstractContainerMenu menu = player.inventoryMenu;

            // Index 0 in the vanilla menu is the crafting RESULT slot; the tool slots start at 46.
            // Shift-clicking OUT needs no patch: vanilla's final else already moves an index it does
            // not recognise into the inventory.
            menu.quickMoveStack(player, VANILLA_INVENTORY_SLOTS);

            helper.assertTrue(ToolSlots.get(player, 0).isEmpty(),
                "the hoe should have left the tool slot");
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_HOE)),
                "the hoe should be back in the inventory");
            helper.succeed();
        });

        // The Container methods vanilla calls during dragging and dropping. They were written and
        // never exercised, which the coverage gate is what noticed.
        FTGameTests.test("the_container_removes_takes_and_clears", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ToolSlotsContainer container = new ToolSlotsContainer(player);
            helper.assertTrue(container.isEmpty(), "a fresh container is empty");
            helper.assertTrue(container.getContainerSize() == ToolSlots.SIZE,
                "the container is the size of the slot set");

            container.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertFalse(container.isEmpty(), "it is not empty once something is in it");
            helper.assertTrue(ToolSlots.get(player, 0).is(Items.DIAMOND_PICKAXE),
                "setItem must reach the attachment, not only the working copy");

            ItemStack removed = container.removeItem(0, 1);
            helper.assertTrue(removed.is(Items.DIAMOND_PICKAXE),
                "removeItem returns what it took, got " + removed.getItem());
            helper.assertTrue(ToolSlots.get(player, 0).isEmpty(), "the removal reached the attachment");

            container.setItem(1, new ItemStack(Items.IRON_AXE));
            ItemStack taken = container.removeItemNoUpdate(1);
            helper.assertTrue(taken.is(Items.IRON_AXE), "removeItemNoUpdate returns the stack");

            container.setItem(2, new ItemStack(Items.SHEARS));
            container.clearContent();
            helper.assertTrue(container.isEmpty(), "clearContent empties it");
            helper.assertTrue(ToolSlots.of(player).isEmpty(), "and that reaches the attachment too");
            helper.succeed();
        });

        // stillValid is what stops a menu surviving into somebody else's hands.
        FTGameTests.test("the_container_belongs_to_one_player", 20, helper -> {
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            ServerPlayer other = helper.makeMockServerPlayerInLevel();
            ToolSlotsContainer container = new ToolSlotsContainer(owner);
            helper.assertTrue(container.stillValid(owner), "the owner may use it");
            helper.assertFalse(container.stillValid(other), "nobody else may");
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

        // THE SLOTS ARE IN VANILLA'S OWN INVENTORY MENU, which is the whole point of the feature and
        // the thing the first version got wrong by opening a screen of its own instead.
        //
        // This is also the only check that the mixin applied at all. Without it the count is 46 and
        // every assertion below fails, which is the failure anyone should see first if mixin
        // infrastructure ever breaks - rather than a silently missing strip in the corner of a
        // screen nobody is looking at during a headless run.
        FTGameTests.test("the_tool_slots_are_in_the_vanilla_inventory_menu", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AbstractContainerMenu menu = player.inventoryMenu;
            int firstToolSlot = VANILLA_INVENTORY_SLOTS;

            helper.assertTrue(menu.slots.size() == VANILLA_INVENTORY_SLOTS + ToolSlots.SIZE,
                "expected " + (VANILLA_INVENTORY_SLOTS + ToolSlots.SIZE) + " slots, found "
                    + menu.slots.size() + " - if this is " + VANILLA_INVENTORY_SLOTS
                    + " the mixin did not apply");

            // APPENDED, not inserted. Vanilla's quickMoveStack reads hardcoded index ranges up to
            // 45, so anything inserted earlier would move the armour, inventory and offhand out
            // from under them and break shift-clicking across the whole screen.
            helper.assertTrue(menu.getSlot(InventoryMenu.SHIELD_SLOT).container instanceof Inventory,
                "the offhand slot must still be the offhand slot");

            // Reads through to the attachment rather than a copy of it.
            ToolSlots.set(player, 0, new ItemStack(Items.NETHERITE_PICKAXE));
            helper.assertTrue(menu.getSlot(firstToolSlot).getItem().is(Items.NETHERITE_PICKAXE),
                "the slot should show what the attachment holds, found "
                    + menu.getSlot(firstToolSlot).getItem().getItem());

            // And writes through it.
            menu.getSlot(firstToolSlot + 1).set(new ItemStack(Items.DIAMOND_AXE));
            helper.assertTrue(ToolSlots.get(player, 1).is(Items.DIAMOND_AXE),
                "placing into the slot should reach the attachment, found "
                    + ToolSlots.get(player, 1).getItem());

            helper.assertFalse(menu.getSlot(firstToolSlot).mayPlace(new ItemStack(Items.COBBLESTONE)),
                "a tool slot must not accept a block");
            helper.assertTrue(menu.getSlot(firstToolSlot).mayPlace(new ItemStack(Items.DIAMOND_HOE)),
                "and must accept a tool");
            helper.succeed();
        });


        // EVERY EMPTY SLOT SAYS WHAT IT IS FOR. Five identical grey squares under the inventory tell
        // a player nothing, so each empty slot draws a vanilla tool outline the way the armour slots
        // opposite them do.
        //
        // A typo in a sprite name renders as a missing texture and is invisible to every other test
        // here, because nothing else in a headless run ever looks at a screen - the same blind spot
        // the registry completeness sweep exists for. The file check is in ToolSlotSpriteTest.
        FTGameTests.test("every_empty_tool_slot_shows_what_goes_in_it", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AbstractContainerMenu menu = player.inventoryMenu;
            Set<Identifier> seen = new HashSet<>();

            int outlined = 0;
            for (int index = 0; index < ToolSlots.SIZE; index++) {
                Identifier icon = menu.getSlot(VANILLA_INVENTORY_SLOTS + index).getNoItemIcon();
                if (icon == null) {
                    // The free slot. Blank on purpose - see ToolSlot.ICONS.
                    continue;
                }
                outlined++;
                helper.assertTrue(seen.add(icon),
                    "tool slot " + index + " repeats an outline (" + icon + "); five slots showing "
                        + "the same picture is the problem this is meant to solve");
                // The sprite FILE cannot be checked from here: a GameTest server runs against a
                // server-filtered jar with no client assets on the classpath, so every path would
                // look missing. ToolSlotSpriteTest does that check in the unit layer, which has the
                // client jar.
                helper.assertTrue(icon.getNamespace().equals("minecraft")
                        && icon.getPath().startsWith("container/slot/"),
                    "outlines should be vanilla's own container sprites, found " + icon);
                helper.assertFalse(icon.getPath().endsWith("/sword"),
                    "no slot should offer a weapon outline; a sword cannot be stored here at all");
            }
            helper.assertTrue(outlined == 4,
                "expected the four tool families to be named, found " + outlined + " outline(s)");
            helper.succeed();
        });

    }
}
