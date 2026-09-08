package com.flatts.flattsthings.gametest;

import java.util.ArrayList;
import java.util.List;
import com.flatts.flattsthings.content.woodcutter.WoodCuttingRecipe;
import com.flatts.flattsthings.content.woodcutter.WoodcutterMenu;
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

    private static Item itemOf(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
    }

    /** The log-ish block a family is cut from, or null if it has none under a known name. */
    private static Item logFor(String wood) {
        for (String suffix : new String[] {"_log", "_stem", "_block"}) {
            Identifier id = Identifier.withDefaultNamespace(wood + suffix);
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.getValue(id);
            }
        }
        return null;
    }

    /** How many {@code want} one {@code input} cuts into, or 0 if that cut is not offered. */
    private static int yieldOf(net.minecraft.gametest.framework.GameTestHelper helper,
                               ItemStack input, Item want) {
        for (RecipeHolder<WoodCuttingRecipe> holder : cuttingFor(helper, input)) {
            ItemStack result = holder.value().assemble(new SingleRecipeInput(input));
            if (result.is(want)) {
                return result.getCount();
            }
        }
        return 0;
    }

    private static int plankYield(net.minecraft.gametest.framework.GameTestHelper helper,
                                  Item log, Item planks) {
        return yieldOf(helper, new ItemStack(log), planks);
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

        // THE AUDITED SET AND NOTHING MORE, which is the negative that keeps the balance argument
        // honest. A cut consumes exactly ONE input, so anything costing more than one plank on a
        // bench would come out proportionally cheaper - a door twice, a trapdoor three times, a
        // fence gate five. Only planks, sticks, buttons, slabs and stairs cost a plank or less, and
        // this fails the moment something dearer appears.
        FTGameTests.test("cutting_planks_makes_only_the_audited_set", 40, helper -> {
            // EVERY FAMILY, NOT JUST OAK. This looked at oak alone, which was defensible while every
            // recipe came out of one uniform loop. log_cuts now branches per family - bamboo has no
            // bark form and a different plank rate - so a mistake in crimson, warped or bamboo had
            // nothing looking at it.
            List<String> wrong = new ArrayList<>();
            for (String wood : woodFamilies()) {
                ItemStack planks = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.withDefaultNamespace(wood + "_planks")));
                List<Item> allowed = List.of(
                    itemOf(wood + "_stairs"), itemOf(wood + "_slab"),
                    itemOf(wood + "_button"), Items.STICK);

                List<Item> offered = cuttingFor(helper, planks).stream()
                    .map(holder -> holder.value().assemble(new SingleRecipeInput(planks)).getItem())
                    .toList();

                for (Item result : offered) {
                    if (!allowed.contains(result)) {
                        wrong.add(wood + " planks -> " + result);
                    }
                }
                // AND EXACTLY THOSE FOUR. Listing what is allowed only catches something dearer
                // sneaking in; it says nothing about one of the four going missing.
                for (Item want : allowed) {
                    if (!offered.contains(want)) {
                        wrong.add(wood + " planks no longer cut " + want);
                    }
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "planks must cut into exactly stairs, a slab, a button and sticks: " + wrong);
            helper.succeed();
        });

        // THE THREE THE AUDIT ADDED, each at exactly the bench rate. A stick is what cutting wood
        // makes and was the most obviously missing thing on the bench; a button is one plank either
        // way; a shelf is six stripped logs for six, so one for one.
        FTGameTests.test("planks_and_logs_cut_the_things_a_bench_makes_from_them", 20, helper -> {
            ItemStack planks = new ItemStack(Items.OAK_PLANKS);
            helper.assertTrue(yieldOf(helper, planks, Items.STICK) == 2,
                "two planks make four sticks on a bench, so one plank should cut two; cut "
                    + yieldOf(helper, planks, Items.STICK));
            helper.assertTrue(yieldOf(helper, planks, Items.OAK_BUTTON) == 1,
                "a button is one plank either way; cut "
                    + yieldOf(helper, planks, Items.OAK_BUTTON));

            ItemStack stripped = new ItemStack(Items.STRIPPED_OAK_LOG);
            helper.assertTrue(yieldOf(helper, stripped, Items.OAK_SHELF) == 1,
                "six stripped logs make six shelves, so one should cut one; cut "
                    + yieldOf(helper, stripped, Items.OAK_SHELF));
            helper.succeed();
        });

        // A LOG BELONGS ON A SAW BENCH, and the first version of this feature refused one - reported
        // with a screenshot of a log in the input offering nothing at all. Derived from the registry
        // the same way the plank sweep is: every family whose planks come from a log-ish tag must
        // cut that log into planks, at vanilla's own rate.
        FTGameTests.test("every_log_cuts_into_its_planks", 40, helper -> {
            List<String> missing = new ArrayList<>();
            List<String> unnamed = new ArrayList<>();
            for (String wood : woodFamilies()) {
                Item logItem = logFor(wood);
                if (logItem == null) {
                    // NOT A SKIP. This used to `continue`, which made the sweep claim more than it
                    // checked: a family whose source block is named by some fourth convention - the
                    // way pale oak arrived - would be quietly passed over while the woodcutter
                    // refused its log, which is the exact failure this test exists to catch.
                    unnamed.add(wood);
                    continue;
                }
                ItemStack log = new ItemStack(logItem);
                boolean planks = cuttingFor(helper, log).stream()
                    .map(holder -> holder.value().assemble(new SingleRecipeInput(log)))
                    .anyMatch(result -> result.getItem() == BuiltInRegistries.ITEM.getValue(
                        Identifier.withDefaultNamespace(wood + "_planks")));
                if (!planks) {
                    missing.add(wood);
                }
            }
            helper.assertTrue(unnamed.isEmpty(),
                "no log-ish block found for these families under _log, _stem or _block, so this"
                    + " sweep cannot speak for them and logFor needs the new name: " + unnamed);
            helper.assertTrue(missing.isEmpty(),
                "these logs could not be cut into their planks: " + missing);
            helper.succeed();
        });

        // THE RATE IS VANILLA'S, NOT A BETTER ONE. A bench turns any log-family block into four
        // planks, and bamboo into two - the one family that breaks the pattern, which is exactly why
        // this asserts a specific number rather than "some planks".
        FTGameTests.test("cutting_a_log_yields_what_a_bench_yields", 20, helper -> {
            helper.assertTrue(plankYield(helper, Items.OAK_LOG, Items.OAK_PLANKS) == 4,
                "an oak log should cut into four planks, cut "
                    + plankYield(helper, Items.OAK_LOG, Items.OAK_PLANKS));
            helper.assertTrue(plankYield(helper, Items.BAMBOO_BLOCK, Items.BAMBOO_PLANKS) == 2,
                "a bamboo block should cut into two, the way a bench does, cut "
                    + plankYield(helper, Items.BAMBOO_BLOCK, Items.BAMBOO_PLANKS));
            helper.succeed();
        });

        // AND THE OTHER THING A SAW DOES TO A LOG. Stripping is the cut the report actually asked
        // for by name, and the bark form is the third.
        FTGameTests.test("a_log_can_be_stripped_and_barked", 20, helper -> {
            ItemStack log = new ItemStack(Items.OAK_LOG);
            List<Item> offered = cuttingFor(helper, log).stream()
                .map(holder -> holder.value().assemble(new SingleRecipeInput(log)).getItem())
                .toList();

            helper.assertTrue(offered.contains(Items.STRIPPED_OAK_LOG),
                "a log should offer a stripped log, offered " + offered);
            helper.assertTrue(offered.contains(Items.OAK_WOOD),
                "and the bark block, offered " + offered);
            helper.assertTrue(offered.size() <= WoodcutterMenu.MAX_OPTIONS,
                "and no more than the menu can show, offered " + offered.size());
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
