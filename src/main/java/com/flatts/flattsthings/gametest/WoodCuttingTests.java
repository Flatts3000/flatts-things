package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.config.FTConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;

/**
 * Planks on a stonecutter.
 *
 * <p>These resolve every recipe through the real {@code RecipeType.STONECUTTING} lookup rather than
 * checking that twenty-four files exist. A recipe naming an item that does not exist is dropped
 * silently during load, with a log line and no failure anywhere, which is exactly what a file check
 * cannot see - the same reason {@code PlateDataTests} exists.
 */
final class WoodCuttingTests {

    /** The twelve families the generator writes. Listed again here on purpose: see the sweep. */
    private static final List<String> WOODS = List.of(
        "acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak",
        "jungle", "mangrove", "oak", "pale_oak", "spruce", "warped");

    private WoodCuttingTests() {
    }

    private static List<RecipeHolder<StonecutterRecipe>> cuttingFor(
            net.minecraft.gametest.framework.GameTestHelper helper, ItemStack input) {
        return helper.getLevel().getServer().getRecipeManager().recipeMap()
            .getRecipesFor(RecipeType.STONECUTTING, new SingleRecipeInput(input),
                helper.getLevel())
            .toList();
    }

    static void register() {
        // EVERY FAMILY, RESOLVED THROUGH THE GAME'S OWN LOOKUP. The list is stated twice - once in
        // the generator, once here - which is the same accepted duplication the plate variants have,
        // and safe for the same reason: this side walks the real recipe manager, so a family the
        // generator forgot fails here rather than shipping half-done.
        FTGameTests.test("every_wood_can_be_cut_into_stairs_and_slabs", 40, helper -> {
            List<String> missing = new ArrayList<>();
            for (String wood : WOODS) {
                ItemStack planks = new ItemStack(helper.getLevel().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                    .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.ITEM,
                        net.minecraft.resources.Identifier.withDefaultNamespace(
                            wood + "_planks")))
                    .value());

                List<String> results = cuttingFor(helper, planks).stream()
                    .map(holder -> holder.value()
                        .assemble(new SingleRecipeInput(planks))
                        .getItem().toString())
                    .collect(java.util.stream.Collectors.toList());

                if (results.stream().noneMatch(r -> r.endsWith(wood + "_stairs"))) {
                    missing.add(wood + " stairs");
                }
                if (results.stream().noneMatch(r -> r.endsWith(wood + "_slab"))) {
                    missing.add(wood + " slab");
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these could not be cut on a stonecutter: " + missing);
            helper.succeed();
        });

        // THE RATIOS ARE COPIED FROM STONE AND THAT IS THE WHOLE BALANCE ARGUMENT. One plank per
        // stair is a third cheaper than the bench's six-for-four; one plank per two slabs is exactly
        // what the bench already charges. Anything more generous would make this a buff rather than
        // the parity fix it is sold as.
        FTGameTests.test("cutting_wood_costs_what_cutting_stone_costs", 20, helper -> {
            ItemStack planks = new ItemStack(Items.OAK_PLANKS);
            int stairs = 0;
            int slab = 0;
            for (RecipeHolder<StonecutterRecipe> holder : cuttingFor(helper, planks)) {
                ItemStack result = holder.value()
                    .assemble(new SingleRecipeInput(planks));
                if (result.is(Items.OAK_STAIRS)) {
                    stairs = result.getCount();
                }
                if (result.is(Items.OAK_SLAB)) {
                    slab = result.getCount();
                }
            }
            helper.assertTrue(stairs == 1, "one plank should cut one stair, cut " + stairs);
            helper.assertTrue(slab == 2, "one plank should cut two slabs, cut " + slab);
            helper.succeed();
        });

        // AND IT MUST NOT HAVE TAUGHT THE STONECUTTER ANYTHING ELSE. Adding recipes to a vanilla
        // recipe type is a wide door: the negative here is that a plank does not become a door, a
        // sign or somebody else's block, which is what a typo in the generator would produce.
        FTGameTests.test("cutting_planks_makes_only_stairs_and_slabs", 20, helper -> {
            ItemStack planks = new ItemStack(Items.OAK_PLANKS);
            List<String> unexpected = new ArrayList<>();
            for (RecipeHolder<StonecutterRecipe> holder : cuttingFor(helper, planks)) {
                ItemStack result = holder.value()
                    .assemble(new SingleRecipeInput(planks));
                if (!result.is(Items.OAK_STAIRS) && !result.is(Items.OAK_SLAB)) {
                    unexpected.add(result.getItem().toString());
                }
            }
            helper.assertTrue(unexpected.isEmpty(),
                "a stonecutter offered more from oak planks than stairs and a slab: " + unexpected);
            helper.succeed();
        });

        // AND STONE STILL CUTS. The feature adds recipes to a vanilla recipe type, so the thing to
        // prove is that it added rather than replaced.
        FTGameTests.test("the_stonecutter_still_cuts_stone", 20, helper -> {
            ItemStack stone = new ItemStack(Blocks.STONE);
            helper.assertTrue(!cuttingFor(helper, stone).isEmpty(),
                "vanilla stone cutting should be untouched");
            helper.succeed();
        });
    }
}
