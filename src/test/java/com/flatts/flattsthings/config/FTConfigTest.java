package com.flatts.flattsthings.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The parts of the config that are answerable without a server.
 *
 * <p><b>The config IS loaded in this layer, and the note here used to say the opposite.</b> moddev's
 * JUnit integration boots a mod context, and {@code FTConfig.SPEC.isLoaded()} is true - measured, by
 * a probe test, after coverage showed the unloaded branch of {@code enabled} was never executed by
 * either suite. Everything written on top of that mistake was wrong with it: the test below asserted
 * nothing at all, and CLAUDE.md told the next person the unit layer could not answer a question it
 * can.
 *
 * <p>What is still true is that {@code ConfigGateTests} is the right home for the GATES. Flipping a
 * switch here would prove {@code FTConfig} reads its own map; it would not prove that the code
 * consulting it stops doing anything, which is the claim worth making and needs a running server.
 *
 * <p>What is here is the feature list, the argument checking, and the recipe condition's parsing -
 * all of which are pure, and none of which a GameTest would answer any better.
 */
class FTConfigTest {

    /**
     * Pins the ids, because they are a published data format rather than an internal name.
     *
     * <p>Each one appears in fourteen generated recipe files as the {@code feature} of a
     * {@code flattsthings:feature_enabled} condition, and in every pack's config file. Renaming one
     * silently turns its recipes off - the condition would name a feature that no longer exists,
     * and there is nowhere for that to be reported.
     */
    @Test
    void everyFeatureIdIsAccountedFor() {
        assertEquals(
            List.of("player_pressure_plates", "tool_slots", "tool_auto_swap",
                "enchanted_golden_apple", "silk_touch_budding_amethyst",
                "gravel_to_flint"),
            List.copyOf(FTConfig.features()),
            "a feature id changed; every generated recipe and every pack's config names these");
    }

    @Test
    void anUnknownFeatureIsRejectedRatherThanAnswered() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> FTConfig.enabled("tool_slotz"));
        assertTrue(thrown.getMessage().contains("tool_slots"),
            "the message should name the features that do exist, was: " + thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> FTConfig.switchFor("tool_slotz"));
    }

    /**
     * The documented fallback, asserted rather than assumed.
     *
     * <p>Reading an unloaded config answers with the shipped default. This test exists because that
     * is the behaviour every other test in this file depends on without saying so, and because the
     * two alternatives - throwing, or answering false - are both silently catastrophic in a context
     * that has no config file.
     */
    /**
     * A fresh config agrees with the declared defaults, for every feature.
     *
     * <p><b>This replaces a test that could not fail.</b> The old one read
     * {@code assertTrue(SPEC.isLoaded() || enabled(f) == defaultOf(f))} on the belief that no config
     * is loaded here. One is, so the left side was always true and the assertion never ran - for
     * every feature, on every run, since it was written.
     *
     * <p>What can honestly be checked is that the values the config came up with match what the code
     * declares, which is the thing a reader of {@code FTConfig} assumes and the thing a mistyped
     * default would break.
     *
     * <p><b>The unloaded fallback in {@code enabled} is unreachable from either suite</b> - the
     * config is loaded in both - so it is deliberately not covered rather than covered by a test
     * that pretends. It exists for contexts neither suite creates, and coverage says so plainly.
     */
    @Test
    void aFreshConfigMatchesTheDeclaredDefaults() {
        assertTrue(FTConfig.SPEC.isLoaded(),
            "this layer is expected to have a loaded config; if that changed, the reasoning in this"
                + " class and in CLAUDE.md needs revisiting rather than this line deleting");
        for (String feature : FTConfig.features()) {
            assertEquals(FTConfig.defaultOf(feature), FTConfig.enabled(feature),
                feature + " reads as " + FTConfig.enabled(feature)
                    + " from a fresh config, but is declared to default to "
                    + FTConfig.defaultOf(feature));
        }
    }

    @Test
    void theRecipeConditionParsesAKnownFeature() {
        DataResult<FeatureCondition> result = FeatureCondition.CODEC.codec()
            .parse(JsonOps.INSTANCE, conditionJson(FTConfig.TOOL_SLOTS));
        assertEquals(FTConfig.TOOL_SLOTS,
            result.getOrThrow(message -> new AssertionError(message)).feature());
    }

    /**
     * A typo has to fail at the file that contains it.
     *
     * <p>The alternative - accepting any string and deciding at test time - turns a misspelled
     * feature into a recipe that is silently always on or always off, with nothing logged and no
     * file named. Validating in the codec is what makes the error point at the data pack.
     */
    @Test
    void theRecipeConditionRejectsAFeatureThatDoesNotExist() {
        DataResult<FeatureCondition> result = FeatureCondition.CODEC.codec()
            .parse(JsonOps.INSTANCE, conditionJson("player_pressure_platez"));
        assertTrue(result.isError(), "an unknown feature should not parse");
        assertTrue(result.error().orElseThrow().message().contains("player_pressure_plates"),
            "and the error should name the features that do exist, was: "
                + result.error().orElseThrow().message());
    }

    private static JsonObject conditionJson(String feature) {
        JsonObject json = new JsonObject();
        json.addProperty("feature", feature);
        return json;
    }
}
