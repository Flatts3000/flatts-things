package com.flatts.flattsthings.gametest;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

/**
 * The recipe vanilla deleted in 1.9, put back.
 *
 * <p><b>Resolved through the real crafting lookup, not read off disk.</b> The completeness sweep
 * proves a recipe file is present; that is a different claim from "these nine items make that one".
 * A recipe naming an item that does not exist is dropped silently during data pack load with a log
 * line and no failure anywhere, and a condition naming a feature that does not exist would drop it
 * the same way - both of which look exactly like a file that is present and correct.
 */
final class EnchantedGoldenAppleTests {

    private EnchantedGoldenAppleTests() {
    }

    static void register() {
        FTGameTests.test("eight_gold_blocks_and_an_apple_craft_a_notch_apple", 30, helper -> {
            ItemStack gold = new ItemStack(Blocks.GOLD_BLOCK.asItem());
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                gold.copy(), gold.copy(), gold.copy(),
                gold.copy(), new ItemStack(Items.APPLE), gold.copy(),
                gold.copy(), gold.copy(), gold.copy()));

            Optional<RecipeHolder<CraftingRecipe>> found = helper.getLevel().getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

            helper.assertTrue(found.isPresent(),
                "eight gold blocks around an apple crafts nothing; the recipe did not load");
            ItemStack result = found.get().value().assemble(input);
            helper.assertTrue(result.is(Items.ENCHANTED_GOLDEN_APPLE),
                "expected an enchanted golden apple, crafted " + result.getItem());
            helper.assertTrue(result.getCount() == 1,
                "expected one, crafted " + result.getCount());
            helper.succeed();
        });

        // THE ORDINARY GOLDEN APPLE MUST STILL BE ITSELF. Eight gold INGOTS around an apple is
        // vanilla's recipe for the plain one, and the two differ only by which gold is used - so a
        // typo of ingot for block in the new recipe would shadow a vanilla recipe rather than fail,
        // and nothing else here would notice.
        FTGameTests.test("the_plain_golden_apple_recipe_is_untouched", 30, helper -> {
            ItemStack ingot = new ItemStack(Items.GOLD_INGOT);
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                ingot.copy(), ingot.copy(), ingot.copy(),
                ingot.copy(), new ItemStack(Items.APPLE), ingot.copy(),
                ingot.copy(), ingot.copy(), ingot.copy()));

            Optional<RecipeHolder<CraftingRecipe>> found = helper.getLevel().getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

            helper.assertTrue(found.isPresent(), "vanilla's golden apple recipe crafts nothing");
            ItemStack result = found.get().value().assemble(input);
            helper.assertTrue(result.is(Items.GOLDEN_APPLE),
                "gold ingots should still make a plain golden apple, crafted " + result.getItem());
            helper.succeed();
        });
    }
}
