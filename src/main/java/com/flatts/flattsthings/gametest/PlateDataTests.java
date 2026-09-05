package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTBlocks.PlateVariant;
import com.flatts.flattsthings.registry.FTItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The data half of the plates: that the JSON actually works, not merely that it exists.
 *
 * <p>{@link RegistryCompletenessTests} asserts a recipe file and a loot table file are PRESENT for
 * every block. That is a different and much weaker claim than these two, and the gap between them is
 * where the realistic bugs live. A recipe naming an ingredient that does not exist is dropped during
 * load with a line in the log and no failure anywhere; a loot table naming the wrong item drops the
 * wrong item, or nothing. Both leave a mod that builds clean, starts clean, passes a file-existence
 * sweep, and is broken the first time a player tries to make or break the block.
 *
 * <p>Twenty-eight JSON files are generated from a table here, so a single wrong f-string would break
 * all of them at once and nothing else in the suite would have noticed.
 */
final class PlateDataTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos PLATE = new BlockPos(1, 1, 1);

    private PlateDataTests() {
    }

    private static void report(GameTestHelper helper, List<String> problems, String what) {
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " " + what + ": " + String.join(", ", problems));
        }
        helper.succeed();
    }

    static void register() {
        // Resolved through the real crafting lookup with a real 2x1 grid, so this proves the recipe
        // CRAFTS rather than that a file parsed. A wrong ingredient id, a wrong result id, or a
        // recipe silently dropped at load all fail here.
        FTGameTests.test("every_variant_has_a_recipe_that_actually_crafts", 30, helper -> {
            List<String> problems = new ArrayList<>();
            for (PlateVariant variant : FTBlocks.VARIANTS) {
                CraftingInput input = CraftingInput.of(2, 1, List.of(
                    new ItemStack(variant.vanilla().asItem()),
                    new ItemStack(Items.REDSTONE)));

                Optional<RecipeHolder<CraftingRecipe>> found = helper.getLevel().getServer()
                    .getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

                if (found.isEmpty()) {
                    problems.add(variant.blockId() + " (vanilla plate + redstone crafts nothing)");
                    continue;
                }
                ItemStack result = found.get().value().assemble(input);
                ItemStack expected = new ItemStack(FTItems.PLATES.get(variant.material()).get());
                if (!ItemStack.isSameItem(result, expected)) {
                    problems.add(variant.blockId() + " (crafts " + result.getItem() + " instead)");
                }
            }
            report(helper, problems, "variants whose recipe does not craft them");
        });

        // Block.getDrops runs the real loot table against a real level, so a table naming the wrong
        // item, or naming an item that does not exist, fails here rather than in someone's world.
        FTGameTests.test("every_variant_drops_itself_when_broken", 30, helper -> {
            List<String> problems = new ArrayList<>();
            for (PlateVariant variant : FTBlocks.VARIANTS) {
                Block plate = FTBlocks.plate(variant.material()).get();
                helper.setBlock(FLOOR, Blocks.STONE);
                helper.setBlock(PLATE, plate);

                BlockPos abs = helper.absolutePos(PLATE);
                List<ItemStack> drops = Block.getDrops(
                    helper.getBlockState(PLATE), helper.getLevel(), abs, null);

                if (drops.size() != 1) {
                    problems.add(variant.blockId() + " (dropped " + drops.size() + " stacks)");
                    continue;
                }
                if (!drops.getFirst().is(FTItems.PLATES.get(variant.material()).get())) {
                    problems.add(variant.blockId() + " (dropped " + drops.getFirst().getItem() + ")");
                }
            }
            report(helper, problems, "variants that do not drop themselves");
        });
    }
}
