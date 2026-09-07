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
     * Every entry this mod adds to a VANILLA tag is optional.
     *
     * <p><b>This guards the worst bug in the repo's history so far, which shipped in a PR and was
     * caught in review.</b> {@code TagLoader.tryBuildTag} drops an entire tag when any REQUIRED
     * entry is missing - the vanilla entries with it - and does nothing louder than one log line.
     * The Blessing enchantment is conditionally loaded, so with its feature switched off the entry
     * would have gone missing and taken {@code #minecraft:in_enchanting_table} with it: no
     * enchanting table anywhere would offer anything, for any item, to any player, and the only
     * evidence would be a line in a log nobody reads.
     *
     * <p>So: anything this mod adds to a tag it does not own must be {@code "required": false},
     * unless it is something this mod always registers unconditionally. The plate tags are the
     * unconditional case - the blocks are registered in Java and cannot fail to exist - which is why
     * this checks the vanilla-namespace tags for entries naming THIS mod and lets plain strings
     * pass only when the feature that owns them cannot switch them off.
     */
    @Test
    void everyEntryAddedToAVanillaTagIsOptional() throws IOException {
        Path vanillaTags = Path.of(System.getProperty("flattsthings.projectDir", "."),
            "src", "main", "resources", "data", "minecraft", "tags");
        assertTrue(Files.isDirectory(vanillaTags), vanillaTags + " is missing");

        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(vanillaTags)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject tag = JsonParser
                    .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray values = tag.getAsJsonArray("values");
                for (int index = 0; index < values.size(); index++) {
                    // A plain string is a required entry. That is only safe for something this mod
                    // registers unconditionally, which is true of the blocks and not of anything
                    // loaded from data behind a feature condition.
                    if (!values.get(index).isJsonPrimitive()) {
                        continue;
                    }
                    String entry = values.get(index).getAsString();
                    if (entry.startsWith("flattsthings:") && CONDITIONAL.contains(entry)) {
                        problems.add(vanillaTags.relativize(file) + " requires " + entry
                            + ", which is loaded behind a feature switch - turning that switch off"
                            + " would delete the whole tag, vanilla entries included");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join(NEWLINE_INDENT, problems));
    }

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
