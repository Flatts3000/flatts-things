package com.flatts.flattsthings.gametest;

import java.util.ArrayList;
import java.util.List;
import com.flatts.flattsthings.content.woodcutter.WoodCuttingRecipe;
import com.flatts.flattsthings.registry.FTRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;

/**
 * Planks on the woodcutter.
 *
 * <p>These resolve every recipe through the real recipe lookup rather than checking that files
 * exist. A recipe naming an item that does not exist is dropped silently during load, with a log
 * line and no failure anywhere, which is exactly what a file check cannot see - the same reason
 * {@code PlateDataTests} exists.
 *
 * <p><b>The expected families are derived from the ITEM REGISTRY, not copied from the generator</b>,
 * and the first version of this file got that wrong. It listed the same twelve names the generator
 * lists and claimed in a comment that this "closes the loop from the other end" the way the plate
 * sweep does. It did not: both sides read the same hand-written list, so a thirteenth wood family in
 * some future 26.x - the way pale oak arrived - would be absent from the generator, absent from here,
 * and every test would pass while the feature silently half-shipped. A review caught the claim.
 *
 * <p>Walking the registry for every {@code <x>_planks} that also has {@code <x>_stairs} and
 * {@code <x>_slab} is what actually closes it: vanilla is the source of truth, and a family Mojang
 * adds fails here until the generator learns about it.
 */
final class WoodCuttingTests {

    private WoodCuttingTests() {
    }

    private static List<RecipeHolder<WoodCuttingRecipe>> cuttingFor(
            net.minecraft.gametest.framework.GameTestHelper helper, ItemStack input) {
        return helper.getLevel().getServer().getRecipeManager().recipeMap()
            .getRecipesFor(FTRecipes.WOOD_CUTTING_TYPE.get(), new SingleRecipeInput(input),
                helper.getLevel())
            .toList();
    }

    /**
     * Every wood family vanilla ships, derived rather than listed: planks that also have stairs and
     * a slab.
     */
    private static List<String> woodFamilies() {
        List<String> families = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (!id.endsWith("_planks")) {
                continue;
            }
            String family = id.substring(0, id.length() - "_planks".length());
            if (BuiltInRegistries.ITEM.containsKey(
                    Identifier.withDefaultNamespace(family + "_stairs"))
                    && BuiltInRegistries.ITEM.containsKey(
                        Identifier.withDefaultNamespace(family + "_slab"))) {
                families.add(family);
            }
        }
        return families;
    }

    static void register() {
        // EVERY FAMILY, RESOLVED THROUGH THE GAME'S OWN LOOKUP. The list is stated twice - once in
        // the generator, once here - which is the same accepted duplication the plate variants have,
        // and safe for the same reason: this side walks the real recipe manager, so a family the
        // generator forgot fails here rather than shipping half-done.
        FTGameTests.test("every_wood_can_be_cut_into_stairs_and_slabs", 40, helper -> {
            List<String> missing = new ArrayList<>();
            for (String wood : woodFamilies()) {
                ItemStack planks = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.withDefaultNamespace(wood + "_planks")));

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
            helper.assertTrue(!woodFamilies().isEmpty(),
                "premise: vanilla has wood families to cut at all");
            helper.assertTrue(missing.isEmpty(),
                "these could not be cut on the woodcutter, which means the generator's table has"
                    + " fallen behind the game: " + missing);
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
            for (RecipeHolder<WoodCuttingRecipe> holder : cuttingFor(helper, planks)) {
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
            for (RecipeHolder<WoodCuttingRecipe> holder : cuttingFor(helper, planks)) {
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

        // AND THE STONECUTTER IS LEFT ALONE, which is the whole reason this is a block of its own.
        // An earlier version of this feature added minecraft:stonecutting recipes so the vanilla
        // cutter would cut wood; that was rejected (owner, 2026-09-07). This pins the reversal:
        // planks offer nothing on a stonecutter, and stone still cuts there.
        FTGameTests.test("the_stonecutter_is_left_alone", 20, helper -> {
            List<RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe>> onStone =
                helper.getLevel().getServer().getRecipeManager().recipeMap()
                    .getRecipesFor(RecipeType.STONECUTTING,
                        new SingleRecipeInput(new ItemStack(Items.OAK_PLANKS)), helper.getLevel())
                    .toList();
            helper.assertTrue(onStone.isEmpty(),
                "a stonecutter must not cut planks; it offered " + onStone.size() + " recipes");
            helper.assertTrue(!helper.getLevel().getServer().getRecipeManager().recipeMap()
                    .getRecipesFor(RecipeType.STONECUTTING,
                        new SingleRecipeInput(new ItemStack(Blocks.STONE)), helper.getLevel())
                    .toList().isEmpty(),
                "and vanilla stone cutting should be untouched");
            helper.succeed();
        });
    }
}
