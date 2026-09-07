package com.flatts.flattsthings.gametest;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Three gravel makes one flint, and fewer does not.
 *
 * <p><b>The count is the balance decision, so the count is what is asserted.</b> A shapeless recipe
 * happily matches a smaller grid unless something says otherwise, so "three gravel makes flint" on
 * its own would pass just as well for a one-gravel recipe.
 *
 * <p><b>Why three, stated correctly.</b> An earlier version of this comment claimed two would beat
 * Fortune I and II while three would not. That is arithmetic nonsense: vanilla's chances are 10, 14.3,
 * 25 and 100 percent, so three gravel per flint is 33 percent per gravel and already beats the first
 * two levels. The real argument is different and better. Gravel that does not roll flint drops as
 * gravel, so with any Fortune shovel you can re-place and re-break until every gravel has become
 * flint - a 1:1 conversion at the cost of a loop. This recipe is a permanent 3:1 with no shovel and
 * no loop, so it is strictly worse in yield than any Fortune level and strictly better than digging
 * unenchanted and accepting the 10 percent. It buys out the tedium without touching the reason to
 * enchant a shovel.
 */
final class GravelToFlintTests {

    private GravelToFlintTests() {
    }

    private static Optional<RecipeHolder<CraftingRecipe>> craft(GameTestHelper helper, int gravel) {
        CraftingInput input = CraftingInput.of(gravel, 1,
            IntStream.range(0, gravel).mapToObj(i -> new ItemStack(Items.GRAVEL)).toList());
        return helper.getLevel().getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    static void register() {
        // THROUGH THE SAME HELPER the negative test uses. Built separately, the helper could hand
        // back an empty input for every arity and the negative test would pass on nothing - checked
        // by doing exactly that, and the suite stayed green.
        FTGameTests.test("three_gravel_craft_one_flint", 30, helper -> {
            Optional<RecipeHolder<CraftingRecipe>> found = craft(helper, 3);
            helper.assertTrue(found.isPresent(), "three gravel crafts nothing");

            CraftingInput input = CraftingInput.of(3, 1, List.of(
                new ItemStack(Items.GRAVEL), new ItemStack(Items.GRAVEL),
                new ItemStack(Items.GRAVEL)));
            ItemStack result = found.get().value().assemble(input);
            helper.assertTrue(result.is(Items.FLINT), "expected flint, crafted " + result.getItem());
            helper.assertTrue(result.getCount() == 1,
                "expected one flint, crafted " + result.getCount());
            helper.succeed();
        });

        FTGameTests.test("fewer_than_three_gravel_craft_nothing", 30, helper -> {
            // THE CONTROL, in the same test. Without it this passes just as green when craft()
            // never built a real input at all - the shape of empty negative this suite keeps
            // catching elsewhere.
            helper.assertTrue(craft(helper, 3).isPresent(),
                "control: three gravel must craft something, or this test proves nothing");
            helper.assertFalse(craft(helper, 1).isPresent(), "one gravel should craft nothing");
            helper.assertFalse(craft(helper, 2).isPresent(), "two gravel should craft nothing");
            helper.succeed();
        });
    }
}
