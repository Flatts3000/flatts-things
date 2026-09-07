package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.registry.FTCreativeTabs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The sweep that catches what registering a thing quietly leaves half-done.
 *
 * <p>Every other test proves a behaviour. These prove <b>coverage</b>: that nothing reached a
 * registry without the files it needs beside it. That gap is silent and never fails a compile - a
 * missing lang key renders as {@code block.flattsthings.player_pressure_plate}, a missing client
 * item definition renders as the pink-and-black missing texture, a missing blockstate renders as a
 * purple cube, and a missing loot table means the block deletes itself when broken.
 *
 * <p>Written with one block in the mod, deliberately. A grab-bag mod grows by accretion, and this
 * is the test that has to exist BEFORE the tenth thing is added, not after the first one ships
 * broken. Ported down from the recompile suite of the same name.
 *
 * <p><b>Why a GameTest can see resources at all.</b> A dedicated server never loads {@code assets/},
 * but the files are still on the classpath in a dev run, so these read them as classpath resources.
 * Lang is different and better: NeoForge loads every mod's {@code en_us} server-side, so a
 * translatable component genuinely resolves here.
 */
final class RegistryCompletenessTests {

    private RegistryCompletenessTests() {
    }

    private static void forEachModItem(BiConsumer<Identifier, Item> action) {
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(FlattsThings.MOD_ID)) {
                action.accept(id, item);
            }
        }
    }

    private static void forEachModBlock(BiConsumer<Identifier, Block> action) {
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals(FlattsThings.MOD_ID)) {
                action.accept(id, block);
            }
        }
    }

    private static boolean resourceExists(String path) {
        return RegistryCompletenessTests.class.getResource(path) != null;
    }

    private static boolean looksLikeARawKey(String rendered) {
        return (rendered.startsWith("item.") || rendered.startsWith("block."))
            && rendered.contains(".")
            && !rendered.contains(" ");
    }

    /** Fail once with the whole list, rather than on whichever entry happened to be walked first. */
    private static void report(GameTestHelper helper, List<String> problems, String what) {
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " " + what + ": " + String.join(", ", problems));
        }
        helper.succeed();
    }

    static void register() {
        // A registry with nothing in it makes every sweep below pass. Assert the mod is actually
        // loaded first, so these cannot all go green on an empty registry.
        FTGameTests.test("the_mod_registered_something", 20, helper -> {
            List<String> ids = new ArrayList<>();
            forEachModItem((id, item) -> ids.add(id.toString()));
            helper.assertTrue(!ids.isEmpty(),
                "no items are registered under " + FlattsThings.MOD_ID
                    + " - every completeness sweep here would pass vacuously");
            helper.succeed();
        });

        FTGameTests.test("every_mod_item_has_a_translated_name", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModItem((id, item) -> {
                String rendered = Component.translatable(item.getDescriptionId()).getString();
                if (looksLikeARawKey(rendered)) {
                    missing.add(id + " -> " + rendered);
                }
            });
            report(helper, missing, "mod items with no en_us translation");
        });

        FTGameTests.test("every_mod_block_has_a_blockstate_file", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModBlock((id, block) -> {
                if (!resourceExists("/assets/" + id.getNamespace() + "/blockstates/"
                        + id.getPath() + ".json")) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "mod blocks with no blockstate JSON (they render as a purple cube)");
        });

        // 26.1 splits the client item definition out of the model. A block model alone is not
        // enough: without assets/<ns>/items/<name>.json the held and inventory form is the
        // missing texture, while the placed block looks perfect.
        FTGameTests.test("every_mod_item_has_a_client_item_definition", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModItem((id, item) -> {
                if (!resourceExists("/assets/" + id.getNamespace() + "/items/"
                        + id.getPath() + ".json")) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "mod items with no client item definition");
        });

        FTGameTests.test("every_mod_block_has_a_loot_table", 20, helper -> {
            List<String> missing = new ArrayList<>();
            forEachModBlock((id, block) -> {
                if (!resourceExists("/data/" + id.getNamespace() + "/loot_table/blocks/"
                        + id.getPath() + ".json")) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "mod blocks with no loot table (they vanish when broken)");
        });

        // The tab's order is a product surface - JEI and EMI read it - and it is the one list
        // nothing else checks. An item left out is invisible in creative and in the JEI panel while
        // working perfectly in every other test here.
        FTGameTests.test("every_mod_item_is_in_the_creative_tab", 20, helper -> {
            CreativeModeTab tab = FTCreativeTabs.FLATTS_THINGS_TAB.get();
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.REGISTRY.allFlags(), true, helper.getLevel().registryAccess()));

            List<Item> shown = new ArrayList<>();
            for (ItemStack stack : tab.getDisplayItems()) {
                shown.add(stack.getItem());
            }
            helper.assertTrue(!shown.isEmpty(),
                "the tab built empty, so a membership check against it would pass for free");

            List<String> missing = new ArrayList<>();
            forEachModItem((id, item) -> {
                if (!shown.contains(item)) {
                    missing.add(id.toString());
                }
            });
            report(helper, missing, "mod items absent from the creative tab");
        });

        // The icon is a Supplier that nothing else ever calls: buildContents does not touch it, so
        // an icon pointing at an unregistered item throws on the CLIENT when the tab is first drawn
        // and never on a server, which is to say never in this suite. One call is enough to prove
        // the supplier resolves.
        FTGameTests.test("the_creative_tab_icon_resolves", 20, helper -> {
            ItemStack icon = FTCreativeTabs.FLATTS_THINGS_TAB.get().getIconItem();
            helper.assertTrue(!icon.isEmpty(), "the creative tab icon must resolve to a real item");
            helper.succeed();
        });

        // ADVANCEMENTS LOAD, AND THE RECIPES THEY UNLOCK EXIST. Both halves fail silently:
        // AdvancementRewards.recipes resolves through recipeManager.byKey(...).stream(), so a key
        // naming a recipe that is not there is quietly dropped and recipe_unlocked never fires. The
        // symptom is a recipe that never enters the recipe book and cannot be crafted at all on a
        // pack running doLimitedCrafting - with every test green.
        FTGameTests.test("every_mod_advancement_unlocks_a_recipe_that_exists", 30, helper -> {
            List<String> problems = new ArrayList<>();
            var server = helper.getLevel().getServer();
            long seen = 0;
            for (var advancement : server.getAdvancements().getAllAdvancements()) {
                if (!advancement.id().getNamespace().equals(FlattsThings.MOD_ID)) {
                    continue;
                }
                seen++;
                for (var recipe : advancement.value().rewards().recipes()) {
                    if (server.getRecipeManager().byKey(recipe).isEmpty()) {
                        problems.add(advancement.id() + " rewards " + recipe.identifier()
                            + ", which is not a loaded recipe");
                    }
                }
            }
            // Counted, so deleting every advancement cannot pass this by leaving nothing to check.
            if (seen == 0) {
                problems.add("this mod loaded no advancements at all");
            }
            report(helper, problems, "advancements rewarding recipes that do not exist");
        });

    }
}
