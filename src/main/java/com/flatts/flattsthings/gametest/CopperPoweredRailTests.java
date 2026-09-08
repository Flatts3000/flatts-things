package com.flatts.flattsthings.gametest;

import java.util.List;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Powered rails from copper, at half the gold recipe's yield.
 *
 * <p><b>The count is the whole feature, so the count is what is asserted.</b> There is no new block
 * here and no new behaviour - a rail made this way is the ordinary vanilla powered rail. What the
 * feature IS is the trade: a metal players have too much of, for fewer rails than the metal they do
 * not. A test that only checked "copper crafts a powered rail" would pass just as happily for a
 * recipe that made six and quietly retired the gold one.
 *
 * <p><b>And the gold recipe still has to work</b>, which is the control - though not for the reason
 * first written here. Two shaped recipes on the same 3x3 footprint with DIFFERENT ingredients cannot
 * collide: a grid of gold matches only vanilla's and a grid of copper only ours. Driving it red
 * proved that, by pointing the copper recipe at gold and watching the gold control pass anyway.
 *
 * <p>What it does guard is the plausible EDIT. Loosen the ingredient to a tag - {@code #c:ingots} is
 * the obvious one, and the obvious thing somebody reaches for when adding bronze or steel - and the
 * copper recipe starts matching a grid of gold too. Then the manager returns whichever it finds
 * first, half of vanilla's yield quietly becomes possible, and nothing reports it. Asking for both
 * metals is what notices.
 */
final class CopperPoweredRailTests {

    private CopperPoweredRailTests() {
    }

    /** The powered rail pattern with `metal` where the gold goes, resolved through the real lookup. */
    private static Optional<RecipeHolder<CraftingRecipe>> craft(GameTestHelper helper,
                                                                net.minecraft.world.item.Item metal) {
        ItemStack m = new ItemStack(metal);
        ItemStack stick = new ItemStack(Items.STICK);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack air = ItemStack.EMPTY;
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            m, air, m,
            m, stick, m,
            m, redstone, m));
        return helper.getLevel().getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    private static ItemStack assemble(GameTestHelper helper, net.minecraft.world.item.Item metal) {
        ItemStack m = new ItemStack(metal);
        CraftingInput input = CraftingInput.of(3, 3, List.of(
            m, ItemStack.EMPTY, m,
            m, new ItemStack(Items.STICK), m,
            m, new ItemStack(Items.REDSTONE), m));
        return craft(helper, metal).orElseThrow().value().assemble(input);
    }

    static void register() {
        FTGameTests.test("copper_crafts_three_powered_rails", 30, helper -> {
            Optional<RecipeHolder<CraftingRecipe>> found = craft(helper, Items.COPPER_INGOT);
            helper.assertTrue(found.isPresent(), "copper in the powered rail pattern crafts nothing");

            ItemStack result = assemble(helper, Items.COPPER_INGOT);
            helper.assertTrue(result.is(Items.POWERED_RAIL),
                "expected a powered rail, crafted " + result.getItem());
            helper.assertTrue(result.getCount() == 3,
                "expected three rails from copper, crafted " + result.getCount()
                    + "; the count is the trade this feature is");
            helper.succeed();
        });

        // THE CONTROL, and NOT for the reason this comment used to give.
        //
        // It said two shaped recipes on one footprint differing by an ingredient is the shape where
        // one shadows the other. That is wrong and was disproved by driving it red: pointing the
        // copper recipe at gold left this test passing, because a grid of gold matches only
        // vanilla's recipe and a grid of copper only ours. Different ingredients cannot collide.
        //
        // Kept and corrected rather than deleted, because the javadoc above was fixed while this was
        // left standing - so for a while the class asserted one thing at the top and the opposite
        // sixty lines down. What this guards is in the javadoc: the plausible EDIT that loosens the
        // ingredient to a tag matching gold too.
        FTGameTests.test("gold_still_crafts_six_powered_rails", 30, helper -> {
            Optional<RecipeHolder<CraftingRecipe>> found = craft(helper, Items.GOLD_INGOT);
            helper.assertTrue(found.isPresent(),
                "the VANILLA gold powered rail recipe no longer resolves at all");

            ItemStack result = assemble(helper, Items.GOLD_INGOT);
            helper.assertTrue(result.is(Items.POWERED_RAIL),
                "gold should still craft a powered rail, crafted " + result.getItem());
            helper.assertTrue(result.getCount() == 6,
                "gold should still craft six, crafted " + result.getCount()
                    + "; if this is three, the copper recipe is answering for gold");
            helper.succeed();
        });
    }
}
