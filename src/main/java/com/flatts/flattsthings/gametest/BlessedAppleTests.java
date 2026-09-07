package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.enchant.BlessedApples;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;

/**
 * Enchanting a golden apple at a real table, through the real menu.
 *
 * <p><b>Driven through {@code EnchantmentMenu.clickMenuButton}, which is what the button in the
 * screen calls.</b> Posting {@code PlayerEnchantItemEvent} by hand would test the handler and
 * nothing else - and this suite has been bitten before by a hand-posted event hiding the fact that
 * nothing real ever posts it. Everything here goes through vanilla: the table decides what to
 * offer, vanilla applies the enchantment, and the swap happens where it would in play.
 *
 * <p>The bookshelves are not decoration. The offer's minimum cost is thirty levels, which a bare
 * table cannot reach, so without a full ring there is nothing to click - which is itself asserted.
 */
final class BlessedAppleTests {

    private static final BlockPos TABLE = new BlockPos(2, 1, 2);

    private BlessedAppleTests() {
    }

    /**
     * A ring of bookshelves two blocks out, at the table's level and one above.
     *
     * <p>Vanilla counts a shelf only when the block between it and the table is air, which the empty
     * plot gives for free.
     */
    private static void surroundWithBookshelves(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean onTheRing = x == 0 || x == 4 || z == 0 || z == 4;
                if (!onTheRing) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, 1, z), Blocks.BOOKSHELF);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.BOOKSHELF);
            }
        }
    }

    /**
     * <b>SURVIVAL, explicitly.</b> {@code makeMockServerPlayerInLevel} comes up in CREATIVE, where
     * {@code hasInfiniteMaterials} short-circuits both the lapis check and the level check in
     * {@code clickMenuButton} - so a creative test passes with no lapis and no experience and proves
     * nothing about the cost this feature is balanced around.
     */
    @SuppressWarnings("removal")
    private static EnchantmentMenu tableWith(GameTestHelper helper, ServerPlayer player,
                                             ItemStack input, int lapis) {
        player.setGameMode(GameType.SURVIVAL);
        helper.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
        EnchantmentMenu menu = new EnchantmentMenu(1, player.getInventory(),
            ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        player.containerMenu = menu;
        menu.getSlot(0).set(input);
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, lapis));
        menu.slotsChanged(menu.getSlot(0).container);
        return menu;
    }


    /** A stored-enchantments component holding one level of Blessing. */
    private static ItemEnchantments blessing(GameTestHelper helper) {
        ItemEnchantments.Mutable held = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        held.set(helper.getLevel().registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(BlessedApples.BLESSING), 1);
        return held.toImmutable();
    }

    static void register() {
        FTGameTests.test("enchanting_a_golden_apple_makes_an_enchanted_one", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.experienceLevel = 60;
            surroundWithBookshelves(helper);
            EnchantmentMenu menu = tableWith(helper, player, new ItemStack(Items.GOLDEN_APPLE), 3);

            helper.assertTrue(menu.costs[2] > 0,
                "the top slot should offer something for a golden apple at a full table, cost was "
                    + menu.costs[2]);
            // clickMenuButton returns true whenever the slot has a cost and an item, INCLUDING when
            // it then finds no enchantment and does nothing at all - so this is a precondition, not
            // proof of anything. The assertions after it are what carry the test.
            helper.assertTrue(menu.clickMenuButton(player, 2),
                "the top slot would not even accept the click");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_GOLDEN_APPLE),
                "expected an enchanted golden apple, slot holds "
                    + menu.getSlot(0).getItem().getItem());
            // AND IT WAS PAID FOR. In survival the levels come off; a test that only checked the
            // item would pass just as well against a version that gave it away.
            helper.assertTrue(player.experienceLevel < 60,
                "enchanting should have cost levels, player still has " + player.experienceLevel);
            helper.assertTrue(menu.getSlot(1).getItem().getCount() < 3,
                "and lapis, slot still holds " + menu.getSlot(1).getItem().getCount());
            helper.succeed();
        });

        // A BARE TABLE MUST NOT DO IT. The cost bracket is the whole balance decision, so a test
        // that only proved enchanting works would pass just as well if the offer appeared for free.
        FTGameTests.test("a_bare_table_cannot_bless_an_apple", 40, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.experienceLevel = 60;
            EnchantmentMenu menu = tableWith(helper, player, new ItemStack(Items.GOLDEN_APPLE), 3);

            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(menu.enchantClue[slot] < 0,
                    "a table with no bookshelves offered something in slot " + slot);
            }
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.GOLDEN_APPLE),
                "and the apple should be untouched");
            helper.succeed();
        });

        // The component is what makes the table accept the apple at all.
        FTGameTests.test("a_golden_apple_is_enchantable", 20, helper -> {
            helper.assertTrue(new ItemStack(Items.GOLDEN_APPLE).isEnchantable(),
                "a golden apple must be enchantable, or the table ignores it");
            helper.assertFalse(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).isEnchantable(),
                "an enchanted golden apple must not be enchantable again");
            // RESOLVED THROUGH THE REGISTRY, not compared against null. The key is a constant and
            // could never be null, so the old version of this proved the field existed rather than
            // that blessing.json parsed, registered, and survived its own condition.
            helper.assertTrue(helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).get(BlessedApples.BLESSING).isPresent(),
                "flattsthings:blessing did not load; the table has nothing to offer");
            helper.succeed();
        });

        // VANILLA'S OWN GOLDEN APPLE IS UNTOUCHED, and this matters more here than it looks. This
        // feature adds a data component to a vanilla item, which is a change to every golden apple
        // in the game - so the plain recipe still crafting a plain golden apple is the guard that
        // the modification stayed narrow. Carried over from the crafting version of this feature,
        // which is otherwise deleted.
        FTGameTests.test("the_plain_golden_apple_recipe_is_untouched", 30, helper -> {
            ItemStack ingot = new ItemStack(Items.GOLD_INGOT);
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                ingot.copy(), ingot.copy(), ingot.copy(),
                ingot.copy(), new ItemStack(Items.APPLE), ingot.copy(),
                ingot.copy(), ingot.copy(), ingot.copy()));

            Optional<RecipeHolder<CraftingRecipe>> found = helper.getLevel().getServer()
                .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

            helper.assertTrue(found.isPresent(), "vanilla's golden apple recipe crafts nothing");
            ItemStack result = found.get().value().assemble(input);
            helper.assertTrue(result.is(Items.GOLDEN_APPLE),
                "gold ingots should still make a plain golden apple, crafted " + result.getItem());
            helper.succeed();
        });

        // AND NOTHING CRAFTS THE ENCHANTED ONE ANY MORE. The recipe this replaces is gone; a test
        // that only proved enchanting works would pass with both routes shipping.
        FTGameTests.test("gold_blocks_no_longer_craft_an_enchanted_apple", 30, helper -> {
            ItemStack block = new ItemStack(Blocks.GOLD_BLOCK.asItem());
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                block.copy(), block.copy(), block.copy(),
                block.copy(), new ItemStack(Items.APPLE), block.copy(),
                block.copy(), block.copy(), block.copy()));

            helper.assertFalse(helper.getLevel().getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isPresent(),
                "eight gold blocks should craft nothing now that enchanting is the route");
            helper.succeed();
        });


        // THE BOOK ROUTE, WHICH SHIPPED WITH NO TEST AT ALL. A book bypasses supported_items, so the
        // table can roll Blessing onto one; that book was a paid-for dead end until an anvil handler
        // made it work. Coverage said 0 of 32 lines on that handler - a path a player spends thirty
        // levels to reach.
        FTGameTests.test("a_blessing_book_turns_an_apple_on_an_anvil", 20, helper -> {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(DataComponents.STORED_ENCHANTMENTS, blessing(helper));

            AnvilUpdateEvent event = new AnvilUpdateEvent(
                new ItemStack(Items.GOLDEN_APPLE), book, null, ItemStack.EMPTY, 0, 0,
                helper.makeMockServerPlayerInLevel());
            NeoForge.EVENT_BUS.post(event);

            helper.assertTrue(event.getOutput().is(Items.ENCHANTED_GOLDEN_APPLE),
                "a Blessing book on a golden apple should give an enchanted golden apple, gave "
                    + event.getOutput().getItem());
            helper.assertTrue(event.getMaterialCost() == 1, "it should consume the book");
            helper.assertTrue(event.getOutput().getCount() == 1,
                "and give exactly one apple, gave " + event.getOutput().getCount());
            helper.succeed();
        });

        // AND IT MUST NOT FIRE ON ANYTHING ELSE. Without this, a handler that ignored its inputs
        // would pass the test above while turning every anvil recipe into golden apples.
        FTGameTests.test("the_anvil_handler_leaves_other_combinations_alone", 20, helper -> {
            ItemStack blessingBook = new ItemStack(Items.ENCHANTED_BOOK);
            blessingBook.set(DataComponents.STORED_ENCHANTMENTS, blessing(helper));
            ItemStack otherBook = new ItemStack(Items.ENCHANTED_BOOK);

            record Case(String what, ItemStack left, ItemStack right) {}
            List<Case> cases = List.of(
                new Case("a plain book on an apple", new ItemStack(Items.GOLDEN_APPLE), otherBook),
                new Case("a Blessing book on a pickaxe",
                    new ItemStack(Items.DIAMOND_PICKAXE), blessingBook),
                new Case("a Blessing book on an already-enchanted apple",
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), blessingBook),
                // THE ONE THAT WAS A DUPLICATION BUG. The handler used to take the output count from
                // the left stack while charging for one book, so this case returned sixty-four
                // enchanted golden apples. Nothing enumerated it, because every other case here is
                // about the wrong ITEMS and this one is about the wrong COUNT.
                new Case("a Blessing book on a whole stack of apples",
                    new ItemStack(Items.GOLDEN_APPLE, 64), blessingBook));

            List<String> wrong = new ArrayList<>();
            for (Case each : cases) {
                AnvilUpdateEvent event = new AnvilUpdateEvent(each.left(), each.right(), null,
                    ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
                NeoForge.EVENT_BUS.post(event);
                if (event.getOutput().is(Items.ENCHANTED_GOLDEN_APPLE)) {
                    wrong.add(each.what());
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "the anvil handler fired on combinations it should ignore: " + wrong);
            helper.succeed();
        });

        // THE GUARD THAT KEEPS THIS MOD OUT OF OTHER MODS' BUSINESS. The swap is conditioned on the
        // enchantment rather than the item, so somebody else making golden apples enchantable does
        // not get their result quietly replaced.
        FTGameTests.test("another_mods_enchantment_on_an_apple_is_left_alone", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
            player.getInventory().setItem(0, apple);

            NeoForge.EVENT_BUS.post(new PlayerEnchantItemEvent(player, apple, List.of(
                new EnchantmentInstance(helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 1))));

            helper.assertTrue(player.getInventory().getItem(0).is(Items.GOLDEN_APPLE),
                "an apple enchanted with something else must stay a golden apple, became "
                    + player.getInventory().getItem(0).getItem());
            helper.succeed();
        });

    }
}
