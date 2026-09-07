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
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
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
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        boolean gated = false;
        for (int index = 0; index < conditions.size(); index++) {
            JsonObject condition = conditions.get(index).getAsJsonObject();
            if (!"flattsthings:feature_enabled".equals(condition.get("type").getAsString())) {
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
