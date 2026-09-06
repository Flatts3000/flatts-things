package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.FlattsThings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world GameTest registrar. Each test is a headless scenario run inside a real server via
 * {@code ./gradlew runGameTestServer} on the shared 5x5x5 plot
 * ({@code data/flattsthings/structure/empty_5x5x5.nbt}), asserting through {@link GameTestHelper}.
 *
 * <p>26.1 registration: a test is a body ({@code Consumer<GameTestHelper>} in
 * {@link Registries#TEST_FUNCTION}) plus metadata (a {@link TestData} carried by a
 * {@link FunctionGameTestInstance} registered at {@link RegisterGameTestsEvent}). {@link #test}
 * hides that two-step so each domain class is bodies plus one registration line.
 */
public final class FTGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, FlattsThings.MOD_ID);

    /** The default plot every test runs on. */
    static final String DEFAULT_STRUCTURE = "empty_5x5x5";

    private record Spec(ResourceKey<Consumer<GameTestHelper>> fn, Identifier structure,
                        Rotation rotation, int maxTicks, String environment) {}

    /** The environment everything runs in unless it says otherwise. */
    static final String DEFAULT_ENVIRONMENT = "default";

    /**
     * An environment nothing else shares, so the test in it runs on its own.
     *
     * <p><b>Tests in one environment run at the same time; environments run one after another.</b>
     * Almost everything here is per-player or per-plot and does not care. The config switches are
     * neither - they are one global value - so a test that turns one off turns it off for every
     * test running alongside it, including tests that never mention the config. That surfaced twice:
     * as {@code switching_the_swap_off_mid_swing_returns_the_item} failing on its own premise with
     * the switch pulled out from under it by a sibling, and, once the config tests were separated
     * from everything else but not from each other, as the same failure again. A shared environment
     * for "the isolated ones" is not isolation.
     *
     * <p>So each config test gets a name of its own here. The environments are registered from
     * whatever the specs actually ask for, rather than from a list kept in step by hand.
     */
    static String aloneIn(String name) {
        return "alone_" + name;
    }

    private static final List<Spec> SPECS = new ArrayList<>();

    private FTGameTests() {
    }

    /** Register one test on the default plot. {@code name} must be lower_snake_case. */
    static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        test(name, maxTicks, DEFAULT_ENVIRONMENT, body);
    }

    /** Register one test in a named environment. See {@link #aloneIn}. */
    static void test(String name, int maxTicks, String environment, Consumer<GameTestHelper> body) {
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder =
            FUNCTIONS.register(name, () -> body);
        SPECS.add(new Spec(holder.getKey(),
            Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, DEFAULT_STRUCTURE),
            Rotation.NONE, maxTicks, environment));
    }

    /** Wire up from the {@link FlattsThings} constructor. */
    public static void register(IEventBus modEventBus) {
        PlayerPressurePlateTests.register();
        ToolSlotTests.register();
        ToolSwapTests.register();
        PlateDataTests.register();
        ConfigGateTests.register();
        RegistryCompletenessTests.register();

        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(FTGameTests::onRegisterGameTests);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (Spec spec : SPECS) {
            environments.computeIfAbsent(spec.environment(), name -> event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, name)));
        }
        for (Spec spec : SPECS) {
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environments.get(spec.environment()), spec.structure(), spec.maxTicks(),
                0,            // setupTicks
                true,         // required (run in CI)
                spec.rotation(),
                false,        // manualOnly (false -> auto CI batch)
                1,            // maxAttempts
                1,            // requiredSuccesses
                false,        // skyAccess
                1);           // padding between batched instances
            event.registerTest(spec.fn().identifier(), new FunctionGameTestInstance(spec.fn(), data));
        }
    }
}
