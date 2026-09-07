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
 * <p><b>What is deliberately NOT here is whether the switches work.</b> No config file is loaded in
 * this layer, so {@link FTConfig#enabled} answers with the shipped default whatever the switch says,
 * and a test asserting a feature is on would pass just as green against a gate that reads the wrong
 * switch or is never reached. That question belongs to {@code ConfigGateTests}, which runs inside a
 * server where the config is real and flips one.
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
                "enchanted_golden_apple", "silk_touch_budding_amethyst"),
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
    @Test
    void anUnloadedConfigReadsAsOn() {
        assertTrue(FTConfig.SPEC.isLoaded() || FTConfig.playerPressurePlates());
        assertTrue(FTConfig.SPEC.isLoaded() || FTConfig.toolSlots());
        assertTrue(FTConfig.SPEC.isLoaded() || FTConfig.toolAutoSwap());
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
