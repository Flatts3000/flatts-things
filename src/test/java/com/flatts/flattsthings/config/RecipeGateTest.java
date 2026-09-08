package com.flatts.flattsthings.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every data file this mod ships that can be gated is behind a feature switch.
 *
 * <p>A recipe is the one part of a feature that cannot be turned off at runtime - nothing removes a
 * loaded one - so the condition in the file IS the off switch. A recipe added without one is
 * always-on for ever, in the recipe book and in JEI, whatever the config says, and nothing else
 * fails when that happens.
 *
 * <p><b>This sweeps the directory rather than a list.</b> {@code tools/test_generate_plates.py}
 * already checks the fourteen plate recipes, but it checks the ones the GENERATOR writes: a
 * hand-written recipe added beside them - which is how the enchanted golden apple arrived - is
 * invisible to it. The failure mode this catches is the next recipe, not this one.
 */
class RecipeGateTest {

    /** Built rather than inlined, so a heredoc cannot eat the escape. */
    private static final String NEWLINE_INDENT = System.lineSeparator() + "  ";

    private static final Path DATA = Path.of(
        System.getProperty("flattsthings.projectDir", "."),
        "src", "main", "resources", "data", "flattsthings");

    /**
     * The directories whose files are gated by a condition read at load.
     *
     * <p>Recipes were the first. A loot modifier is the same shape - the folder is scanned, the
     * file is live for the session, and nothing removes it at runtime - so it belongs here. So is
     * an enchantment. Each was outside the sweep for exactly one PR before somebody noticed, which
     * is the argument for keeping the list rather than against it.
     *
     * <p><b>Tags are still not here, but the reason first written down was wrong.</b> It said a tag
     * entry for a disabled feature is inert because tags merge. Merging is about two packs writing
     * the same tag; it says nothing about an entry pointing at something that failed to load.
     * {@code TagLoader.tryBuildTag} drops the WHOLE tag when any REQUIRED entry is missing - the
     * vanilla entries with it - and only logs. So a required entry naming a conditionally-loaded
     * thing breaks a vanilla system for everyone who switches that feature off. The answer is
     * {@code "required": false} on the entry rather than a condition on the tag file, which is why
     * sweeping for conditions would not have caught it and tags still do not belong in this list.
     */
    private static final List<String> GATED = List.of("recipe", "loot_modifiers", "enchantment");

    @Test
    void everyGatedDataFileNamesAKnownFeature() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String folder : GATED) {
            Path root = DATA.resolve(folder);
            assertTrue(Files.isDirectory(root), root.toAbsolutePath() + " is missing");
            // WALK, NOT LIST. Minecraft's loaders recurse and fold the subpath into the id
            // (recipe/food/x.json -> flattsthings:food/x), and this mod already nests one level
            // down in loot_table/blocks/. A non-recursive sweep would stay green for the first file
            // put in a subdirectory - precisely the case this class says it exists to catch.
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json")).toList()) {
                    problems.addAll(check(file));
                }
            }
        }
        assertTrue(problems.isEmpty(),
            "data files that cannot be switched off:\n  " + String.join("\n  ", problems));
    }

    /**
     * Every hand-written gated file names the feature it is supposed to name.
     *
     * <p>The sweep above proves a gated file names a feature that EXISTS. That is not enough on its
     * own: each of these was written by copying the last one, and a {@code feature} field left
     * unchanged in the copy gives a file gated on a real switch - the wrong one. Everything stays
     * green, because every in-world test runs with all features on, while one switch does nothing
     * and another turns off two features.
     *
     * <p>The table is deliberately hand-kept and deliberately complete: a new gated file that is not
     * listed here fails, so adding one forces a decision about which switch owns it.
     *
     * <p><b>Why a file assertion rather than a runtime one.</b> These gates are read when the data
     * pack loads, so flipping a switch in a running server changes nothing until a reload and there
     * is no in-world state to assert against. The chain is: this file names this feature, the
     * GameTest {@code the_recipe_condition_follows_the_switch} proves the condition follows that
     * switch, and the feature's own tests prove it works when it loads.
     */
    @Test
    void everyHandWrittenGatedFileNamesTheFeatureItShould() throws IOException {
        Map<String, String> expected = Map.of(
            "enchantment/blessing.json", FTConfig.ENCHANTED_GOLDEN_APPLE,
            "recipe/flint_from_gravel.json", FTConfig.GRAVEL_TO_FLINT,
            "recipe/powered_rail_from_copper.json", FTConfig.COPPER_POWERED_RAILS,
            "recipe/woodcutter.json", FTConfig.WOOD_CUTTING,
            "loot_modifiers/silk_touch_budding_amethyst.json",
            FTConfig.SILK_TOUCH_BUDDING_AMETHYST);

        List<String> problems = new ArrayList<>();
        for (String folder : GATED) {
            Path root = DATA.resolve(folder);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json")).toList()) {
                    String key = folder + "/" + root.relativize(file).toString().replace(File.separatorChar, '/');
                    // The fourteen plate recipes are generated from one template, so they cannot
                    // drift from each other the way a hand-copied file can. tools/ checks those.
                    if (key.endsWith("_player_pressure_plate.json")) {
                        continue;
                    }
                    // The wood cutting recipes are generated the same way, from one template per
                    // shape, so listing twenty-four names would be a copy of the generator rather
                    // than a check on it. CHECKED AS A FAMILY rather than skipped, though: the
                    // template's feature is exactly the thing a copy-paste would get wrong, and it
                    // is one assertion instead of twenty-four lines.
                    if (key.endsWith("_woodcutting.json")) {
                        if (!features(file).contains(FTConfig.WOOD_CUTTING)) {
                            problems.add(key + " should be gated on '" + FTConfig.WOOD_CUTTING
                                + "' but names " + features(file));
                        }
                        continue;
                    }
                    // The terrain slabs are generated from one template too, and are checked as a
                    // family for the same reason as the wood cuts: naming nine files here would
                    // copy the generator rather than check it, while the template's feature field
                    // is exactly what a copy-paste gets wrong. Covers the recipe and its unlock
                    // advancement, which are written by the same function and can drift together.
                    if (key.endsWith("_slab.json")) {
                        if (!features(file).contains(FTConfig.TERRAIN_SLABS)) {
                            problems.add(key + " should be gated on '" + FTConfig.TERRAIN_SLABS
                                + "' but names " + features(file));
                        }
                        continue;
                    }
                    String want = expected.get(key);
                    if (want == null) {
                        problems.add(key + " is gated but not listed here; say which switch owns it");
                        continue;
                    }
                    if (!features(file).contains(want)) {
                        problems.add(key + " should be gated on '" + want + "' but names "
                            + features(file));
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join(NEWLINE_INDENT, problems));
    }

    /** Every feature named by a feature_enabled condition in one file. */
    private static List<String> features(Path file) throws IOException {
        JsonObject json = JsonParser
            .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> named = new ArrayList<>();
        JsonArray conditions = json.getAsJsonArray("neoforge:conditions");
        for (int index = 0; index < conditions.size(); index++) {
            JsonObject condition = conditions.get(index).getAsJsonObject();
            if ("flattsthings:feature_enabled".equals(condition.get("type").getAsString())) {
                named.add(condition.get("feature").getAsString());
            }
        }
        return named;
    }

    /**
     * Every tag entry that CAN fail to exist is optional, in every tag file this mod ships.
     *
     * <p><b>This replaces two narrower tests and is a strict superset of both.</b> The first walked
     * only {@code data/minecraft/tags} and flagged only {@code flattsthings:} entries on
     * {@link #CONDITIONAL}. The second, added with the recompile entries, walked only
     * {@code data/flattsthings/tags} and flagged only FOREIGN namespaces. Between them sat two holes
     * that a review found, and both are worse than what either test covered:
     *
     * <ul>
     *   <li>A foreign entry in a VANILLA tag. Writing {@code "recompile:junk_shovel"} as a plain
     *       string into {@code data/minecraft/tags/block/mineable/shovel.json} discards
     *       {@code #minecraft:mineable/shovel} on every install without recompile, so nothing in the
     *       game is shovel-mineable. A strictly worse blast radius than either test's own case.</li>
     *   <li>A CONDITIONAL entry of ours in a tag of ours. The namespace rule exempted
     *       {@code flattsthings:} on the belief that this mod always registers what it names, which
     *       is exactly what {@code CONDITIONAL} exists to say is untrue.</li>
     * </ul>
     *
     * <p><b>The bug this all descends from is the worst the repo has shipped</b>, caught in review
     * rather than by a test. {@code TagLoader.tryBuildTag} drops an ENTIRE tag when any required
     * entry is missing - the vanilla entries with it - and does nothing louder than one log line.
     * {@code #minecraft:in_enchanting_table} listed the conditionally-loaded Blessing enchantment,
     * so switching that feature off would have stopped every enchanting table in the game offering
     * anything to anybody.
     *
     * <p>It is not hypothetical for the tool slots either, and that was measured rather than argued:
     * making one recompile entry required and running the suite without recompile - CI's ordinary
     * state - failed six tool slot tests, including a netherite pickaxe being refused by a slot.
     *
     * <p><b>By namespace and by condition, never by a list of known mods.</b> Anything outside
     * {@code minecraft} can fail to load, so the rule is about where an entry comes from rather than
     * which mod it is, and the next compat entry is covered the day it is written.
     */
    @Test
    void everyTagEntryThatCanFailToExistIsOptional() throws IOException {
        // Every namespace, not just ours: DATA is data/flattsthings, and its parent is data.
        // Derived rather than spelled out a second time, so the two cannot drift.
        Path allData = DATA.getParent();
        assertTrue(Files.isDirectory(allData), allData + " is missing");

        List<Path> tagFiles;
        try (Stream<Path> files = Files.walk(allData)) {
            tagFiles = files.filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".json"))
                .filter(RecipeGateTest::underATagsDirectory)
                .toList();
        }
        // A sweep that found nothing to sweep passes for the wrong reason. This mod ships tag files
        // in two namespaces; if the walk ever finds none, the path moved rather than the risk going
        // away.
        assertTrue(!tagFiles.isEmpty(), "no tag files found under " + allData);

        List<String> problems = new ArrayList<>();
        for (Path file : tagFiles) {
            JsonObject tag = JsonParser
                .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray values = tag.getAsJsonArray("values");
            if (values == null) {
                // A tag file may legitimately carry only "remove" and/or "replace". Skipping is
                // right; skipping SILENTLY on a null this code did not expect is how a typo in
                // "values" turns the whole sweep into a no-op, so it is named instead.
                problems.add(allData.relativize(file) + " has no \"values\" array; if that is"
                    + " deliberate this check needs to learn about it, and if it is a typo then the"
                    + " tag is not doing anything");
                continue;
            }
            for (int index = 0; index < values.size(); index++) {
                // A plain string is a REQUIRED entry. An object may or may not be, so the id has to
                // be read out of both shapes and only the object can pass.
                boolean required = true;
                String entry;
                if (values.get(index).isJsonPrimitive()) {
                    entry = values.get(index).getAsString();
                } else {
                    JsonObject object = values.get(index).getAsJsonObject();
                    entry = object.get("id").getAsString();
                    required = !object.has("required") || object.get("required").getAsBoolean();
                }
                if (required && canFailToExist(entry)) {
                    problems.add(allData.relativize(file) + " requires " + entry
                        + ", which need not exist when the tag is built - a missing required entry"
                        + " makes TagLoader discard the WHOLE tag, vanilla entries included");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join(NEWLINE_INDENT, problems));
    }

    /** Whether a path sits inside a {@code tags} directory, at any depth under a namespace. */
    private static boolean underATagsDirectory(Path file) {
        for (Path part : file) {
            if (part.toString().equals("tags")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a tag entry names something that might not be registered when the tag is built.
     *
     * <p>Two ways that happens, and the second is the one a namespace check alone misses: the thing
     * belongs to a mod that need not be installed, or it is ours but loaded from data behind a
     * feature switch.
     */
    private static boolean canFailToExist(String entry) {
        String id = entry.startsWith("#") ? entry.substring(1) : entry;
        int colon = id.indexOf(':');
        // No namespace at all means minecraft, which is always present.
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        if (!namespace.equals("minecraft") && !namespace.equals("flattsthings")) {
            return true;
        }
        return CONDITIONAL.contains(id);
    }

    /**
     * The entries in {@code #flattsthings:tool_slot_valid} that need not exist, as an ALLOW-LIST.
     *
     * <p><b>"Need not exist" rather than "from another mod"</b>, which is what this said first and
     * was caught by driving it red: {@link #canFailToExist} also answers true for one of OUR ids
     * loaded behind a feature switch, and such an entry deserves the same look. The set is every
     * entry whose presence is not guaranteed, whoever registers it.
     *
     * <p><b>This exists because the runtime test cannot cover those entries, and saying otherwise
     * was an overstatement a review caught.</b> {@code every_tool_slot_entry_breaks_blocks} walks
     * the RESOLVED tag, so without recompile installed - which is CI's ordinary state and the only
     * one it runs in - all five optional entries resolve to nothing and the loop sees vanilla tools
     * only, every one of which carries {@code DataComponents.TOOL} by construction. A follow-up PR
     * adding {@code recompile:copper_garbage_vacuum} would pass the whole suite green and make the
     * vacuum storable in a tool slot, which is the exact thing the owner ruled out on 2026-09-08.
     *
     * <p><b>An allow-list rather than a deny-list of vacuums</b>, deliberately. A deny-list pins one
     * ruling; this fails on ANY new foreign entry, so whoever adds the next one has to come here and
     * read the rule before the suite goes green. That is the point - the rule needs a human in front
     * of it, because whether breaking blocks is a thing's JOB is not a question a test can answer
     * for an item it cannot load.
     *
     * <p>The rule itself, from both sides: a thing belongs in a tool slot when it breaks blocks AND
     * breaking blocks is its job. Every id below carries {@code DataComponents.TOOL} - read out of
     * recompile's own source rather than assumed - and every one is a mining tool rather than a
     * weapon.
     */
    @Test
    void theOptionalEntriesInTheToolSlotTagAreTheOnesThatWereRuledIn() throws IOException {
        Path tag = DATA.resolve(Path.of("tags", "item", "tool_slot_valid.json"));
        JsonArray values = JsonParser
            .parseString(Files.readString(tag, StandardCharsets.UTF_8)).getAsJsonObject()
            .getAsJsonArray("values");

        List<String> optional = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            String entry = values.get(index).isJsonPrimitive()
                ? values.get(index).getAsString()
                : values.get(index).getAsJsonObject().get("id").getAsString();
            if (canFailToExist(entry)) {
                optional.add(entry);
            }
        }

        assertTrue(RULED_IN_OPTIONAL_ENTRIES.equals(optional),
            "the entries in tool_slot_valid.json that need not exist are " + optional
                + ", expected " + RULED_IN_OPTIONAL_ENTRIES
                + ". Anything new here has to satisfy the entry rule -"
                + " it breaks blocks, and breaking blocks is its job - which no test can check for"
                + " an item it cannot load. Recompile's Garbage Vacuums were ruled OUT on that rule"
                + " (owner, 2026-09-08): they break no blocks.");
    }

    /** The not-guaranteed entries ruled into the tool slots, in file order (issue 66). */
    private static final List<String> RULED_IN_OPTIONAL_ENTRIES = List.of(
        "#recompile:sledgehammer",
        "recompile:cutting_torch",
        "recompile:scrap_knife",
        "recompile:prybar",
        "recompile:junk_shovel");


    /** Things this mod loads from data behind a condition, so they can fail to exist. */
    private static final List<String> CONDITIONAL = List.of("flattsthings:blessing");

    private static List<String> check(Path file) throws IOException {
        List<String> problems = new ArrayList<>();
        JsonObject recipe = JsonParser
            .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!recipe.has("neoforge:conditions")) {
            problems.add(file.getFileName() + ": no neoforge:conditions");
            return problems;
        }
        // EVERY MALFORMED SHAPE NAMES THE FILE. A hand-edited conditions block with no type, no
        // feature, or an object where an array belongs is exactly the slip this test polices, and a
        // raw NPE or ClassCastException would hand the developer a stack trace with no filename -
        // from the one test whose whole output is meant to be which file is wrong.
        if (!recipe.get("neoforge:conditions").isJsonArray()) {
            problems.add(file.getFileName() + ": neoforge:conditions is not an array");
            return problems;
        }
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        boolean gated = false;
        for (int index = 0; index < conditions.size(); index++) {
            if (!conditions.get(index).isJsonObject()) {
                problems.add(file.getFileName() + ": condition " + index + " is not an object");
                continue;
            }
            JsonObject condition = conditions.get(index).getAsJsonObject();
            if (!condition.has("type")) {
                problems.add(file.getFileName() + ": condition " + index + " has no type");
                continue;
            }
            if (!"flattsthings:feature_enabled".equals(condition.get("type").getAsString())) {
                continue;
            }
            if (!condition.has("feature")) {
                problems.add(file.getFileName() + ": a feature_enabled condition with no feature");
                continue;
            }
            String feature = condition.get("feature").getAsString();
            if (!FTConfig.isFeature(feature)) {
                problems.add(file.getFileName() + ": names feature '" + feature
                    + "', which does not exist, so the recipe silently never loads");
            }
            gated = true;
        }
        if (!gated) {
            problems.add(file.getFileName() + ": has conditions but none is feature_enabled");
        }
        return problems;
    }
}
