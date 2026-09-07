package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.enchant.BlessedApples;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

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

    @SuppressWarnings("removal")
    private static EnchantmentMenu tableWith(GameTestHelper helper, ServerPlayer player,
                                             ItemStack input, int lapis) {
        helper.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
        EnchantmentMenu menu = new EnchantmentMenu(1, player.getInventory(),
            ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        player.containerMenu = menu;
        menu.getSlot(0).set(input);
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, lapis));
        menu.slotsChanged(menu.getSlot(0).container);
        return menu;
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
            helper.assertTrue(menu.clickMenuButton(player, 2), "the top slot refused the click");
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.ENCHANTED_GOLDEN_APPLE),
                "expected an enchanted golden apple, slot holds "
                    + menu.getSlot(0).getItem().getItem());
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
            helper.assertTrue(BlessedApples.BLESSING != null, "the enchantment key exists");
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

    }
}
