package com.flatts.flattsthings.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * The recipe condition that reads {@link FTConfig}: {@code flattsthings:feature_enabled}.
 *
 * <p><b>A recipe is data, so turning it off has to be done in data.</b> There is no runtime call
 * that removes a recipe once it is loaded, and stripping one afterwards would leave it in the recipe
 * book and in JEI. NeoForge evaluates conditions while the recipe is being read, so a recipe whose
 * condition is false is never loaded at all - which is the only version of "off" that is true
 * everywhere a player can look.
 *
 * <p>The feature name is validated while parsing rather than while testing. A typo in a pack's own
 * data pack should fail loudly at the file that contains it, naming the features that do exist, and
 * a condition that has parsed is then known to be answerable.
 */
public record FeatureCondition(String feature) implements ICondition {

    private static final Codec<String> FEATURE_CODEC = Codec.STRING.validate(feature ->
        FTConfig.isFeature(feature)
            ? DataResult.success(feature)
            : DataResult.error(() -> feature + " is not a feature of Flatts's Things; known"
                + " features are " + FTConfig.features()));

    public static final MapCodec<FeatureCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            FEATURE_CODEC.fieldOf("feature").forGetter(FeatureCondition::feature)
        ).apply(instance, FeatureCondition::new));

    @Override
    public boolean test(IContext context) {
        return FTConfig.enabled(this.feature);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "feature_enabled(\"" + this.feature + "\")";
    }
}
