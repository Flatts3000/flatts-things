package com.flatts.flattsthings.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every recipe this mod ships is behind a feature switch.
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

    private static final Path RECIPES = Path.of(
        System.getProperty("flattsthings.projectDir", "."),
        "src", "main", "resources", "data", "flattsthings", "recipe");

    @Test
    void everyShippedRecipeIsGatedOnAKnownFeature() throws IOException {
        assertTrue(Files.isDirectory(RECIPES), RECIPES.toAbsolutePath() + " is missing");
        List<String> problems = new ArrayList<>();
        // WALK, NOT LIST. Minecraft's recipe loader recurses and folds the subpath into the id
        // (recipe/food/x.json -> flattsthings:food/x), and this mod already nests one level down in
        // loot_table/blocks/. A non-recursive sweep would stay green for the first recipe put in a
        // subdirectory - which is precisely the case this class says it exists to catch.
        try (Stream<Path> files = Files.walk(RECIPES)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json")).toList()) {
                problems.addAll(check(file));
            }
        }
        assertTrue(problems.isEmpty(),
            "recipes that cannot be switched off:\n  " + String.join("\n  ", problems));
    }

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
