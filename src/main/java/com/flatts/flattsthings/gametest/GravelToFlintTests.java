package com.flatts.flattsthings.gametest;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Three gravel makes one flint, and two does not.
 *
 * <p><b>The count is the balance decision, so it is the thing asserted.</b> Vanilla drops flint from
 * gravel one time in ten, and Fortune III already gets it from every one - so three sits between the
 * two and gives an unenchanted player a floor without beating the enchantment. Two would beat
 * Fortune I and II, which is the line worth not crossing, and a shapeless recipe happily matches a
 * smaller grid unless something says otherwise.
 */
final class GravelToFlintTests {

    private GravelToFlintTests() {
    }

    private static Optional<RecipeHolder<CraftingRecipe>> craft(
            net.minecraft.gametest.framework.GameTestHelper helper, int gravel) {
        CraftingInput input = CraftingInput.of(gravel, 1,
            java.util.stream.IntStream.range(0, gravel)
                .mapToObj(i -> new ItemStack(Items.GRAVEL)).toList());
        return helper.getLevel().getServer().getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    static void register() {
        FTGameTests.test("three_gravel_craft_one_flint", 30, helper -> {
            CraftingInput input = CraftingInput.of(3, 1, List.of(
                new ItemStack(Items.GRAVEL), new ItemStack(Items.GRAVEL),
                new ItemStack(Items.GRAVEL)));
            Optional<RecipeHolder<CraftingRecipe>> found = helper.getLevel().getServer()
                .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

            helper.assertTrue(found.isPresent(), "three gravel crafts nothing");
            ItemStack result = found.get().value().assemble(input);
            helper.assertTrue(result.is(Items.FLINT),
                "expected flint, crafted " + result.getItem());
            helper.assertTrue(result.getCount() == 1,
                "expected one flint, crafted " + result.getCount());
            helper.succeed();
        });

        // THE RATE, ASSERTED. Without this, a recipe of one or two gravel would pass the test above
        // while being a straight upgrade over a Fortune shovel - which is the one outcome the count
        // was chosen to avoid.
        FTGameTests.test("fewer_than_three_gravel_craft_nothing", 30, helper -> {
            helper.assertFalse(craft(helper, 1).isPresent(), "one gravel should craft nothing");
            helper.assertFalse(craft(helper, 2).isPresent(), "two gravel should craft nothing");
            helper.succeed();
        });
    }
}
