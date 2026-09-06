package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FeatureCondition;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data-pack conditions this mod adds. One so far: {@code flattsthings:feature_enabled}. */
public final class FTConditions {

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
        DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, FlattsThings.MOD_ID);

    /**
     * <b>The registered name is part of the data format.</b> It appears in every generated recipe
     * as {@code "type": "flattsthings:feature_enabled"}, so renaming it silently drops every recipe
     * that names the old one - a data pack cannot report an unknown condition type as an error
     * without refusing to load the world.
     */
    public static final Supplier<MapCodec<FeatureCondition>> FEATURE_ENABLED =
        CONDITION_CODECS.register("feature_enabled", () -> FeatureCondition.CODEC);

    private FTConditions() {
    }

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}
