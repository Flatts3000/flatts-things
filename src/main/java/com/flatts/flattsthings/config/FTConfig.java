package com.flatts.flattsthings.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every feature in this mod, and a switch for each one.
 *
 * <p><b>A grab bag has to be a menu rather than a package deal.</b> Nothing here gates anything
 * else, so a pack that wants the tool slots and not the pressure plates is not asking for anything
 * unreasonable - it is asking for the only thing a mod shaped like this can sensibly offer. Every
 * feature is on by default and can be turned off alone.
 *
 * <p><b>COMMON, not SERVER, and that is a decision rather than an oversight.</b> These are content
 * switches: they decide whether a recipe loads and whether an item appears in the creative tab.
 * Both of those are read on the client outside any world - the creative tab builds its contents at
 * startup - and a SERVER config is not loaded then, so reading one would throw before a world
 * exists. Nothing here needs a per-world value or a synced one, which is exactly the case COMMON
 * still exists for.
 *
 * <p><b>What "disabled" means is deliberately narrow: no new ones.</b> A disabled feature stops
 * being craftable and disappears from the creative tab, and disabled behaviour stops running.
 * Blocks already placed in a world keep working and items already stored stay where they are.
 * Making a switch delete somebody's build is a far worse failure than leaving a block that a pack
 * author would rather was gone, and it is not reversible by flipping the switch back.
 */
public final class FTConfig {

    /**
     * The player-only pressure plate family.
     *
     * <p>Off means no recipes and nothing in the creative tab. Placed plates keep sensing players,
     * because a plate that silently stopped working would read as a broken redstone contraption
     * rather than as a setting.
     */
    public static final String PLAYER_PRESSURE_PLATES = "player_pressure_plates";

    /** The dedicated tool slots. Off hides them on the screen; stored tools stay stored. */
    public static final String TOOL_SLOTS = "tool_slots";

    /**
     * Making an enchanted golden apple by enchanting a golden apple.
     *
     * <p>Off means the enchantment is never offered and the item goes back to being loot-only, the
     * way vanilla leaves it.
     */
    public static final String ENCHANTED_GOLDEN_APPLE = "enchanted_golden_apple";

    /** Swapping the right tool into the hand when you start breaking a block. */
    public static final String TOOL_AUTO_SWAP = "tool_auto_swap";

    /**
     * Silk touch picks up budding amethyst, which vanilla never lets you take.
     *
     * <p>Off leaves it unobtainable, which is where vanilla puts it and why: an amethyst farm that
     * cannot be moved is the point of the omission.
     */
    public static final String SILK_TOUCH_BUDDING_AMETHYST = "silk_touch_budding_amethyst";

    /**
     * Three gravel on a bench makes one flint.
     *
     * <p>A floor under vanilla's ten percent drop, and deliberately worse than Fortune III, which
     * already guarantees flint from every gravel.
     */
    public static final String GRAVEL_TO_FLINT = "gravel_to_flint";

    /** Insertion-ordered, because it is also the order the switches appear in the file. */
    private static final Map<String, ModConfigSpec.BooleanValue> FEATURES = new LinkedHashMap<>();

    /**
     * What each feature answers before the config file is loaded.
     *
     * <p>Kept separately because {@link ModConfigSpec.BooleanValue} will not give up its default
     * without a loaded config, and the unloaded answer has to be each feature's own default rather
     * than a blanket yes. Every feature ships on today, so the two agree - but the blanket yes was
     * a coincidence rather than a rule, and the first feature to ship off would have been quietly
     * on in exactly the contexts where nothing is loaded.
     */
    private static final Map<String, Boolean> DEFAULTS = new LinkedHashMap<>();

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
            "Every feature in Flatts's Things, and a switch for each one.",
            "",
            "Turning one off stops NEW ones: no recipe, nothing in the creative tab, and the",
            "behaviour stops running. It never deletes anything. Blocks already placed keep",
            "working and tools already in a slot stay in it, so a switch is always safe to flip",
            "back.")
            .push("features");

        define(builder, PLAYER_PRESSURE_PLATES,
            "The player-only pressure plates: one per vanilla plate, crafted from that plate plus",
            "redstone. Off removes the recipes and hides them from the creative tab. Plates already",
            "placed in a world keep sensing players.");

        define(builder, TOOL_SLOTS,
            "Five dedicated tool slots on the inventory screen that do not take up inventory",
            "space. Off hides them and refuses clicks; tools already stored stay stored and come",
            "back when this is turned on again.");

        define(builder, TOOL_AUTO_SWAP,
            "Swap the best tool for the job into your hand while you break a block, and put your own",
            "item back when you stop. Needs " + TOOL_SLOTS + ", since it swaps out of those slots -",
            "turning those off turns this off with them whatever this says.");

        define(builder, ENCHANTED_GOLDEN_APPLE,
            "Enchant a golden apple at an enchanting table to turn it into an enchanted golden",
            "apple. Needs a full ring of bookshelves: the offer only appears at thirty levels, so a",
            "bare table cannot reach it. Off leaves the item loot-only, where vanilla left it.");

        define(builder, SILK_TOUCH_BUDDING_AMETHYST,
            "Let silk touch pick up budding amethyst, which vanilla never drops. Note this is a",
            "deliberate vanilla restriction rather than an oversight: an unobtainable budding block",
            "is what stops an amethyst farm being picked up and moved, so turning this on makes",
            "geodes portable. On by default like everything else here, because a mod nobody",
            "switched on is a mod that appears not to work - turn it off if your pack wants",
            "vanilla's restriction.");

        define(builder, GRAVEL_TO_FLINT,
            "Craft one flint from three gravel. Vanilla drops flint one time in ten, and gravel",
            "that does not roll flint drops as gravel - so with any Fortune shovel you can",
            "re-place and re-break until every gravel has become flint. This buys that loop out at",
            "three to one: worse than any Fortune level in yield, better than digging unenchanted.");

        builder.pop();
        SPEC = builder.build();
    }

    private FTConfig() {
    }

    private static void define(ModConfigSpec.Builder builder, String feature, String... comment) {
        define(builder, feature, true, comment);
    }

    /**
     * A feature with a default of its own.
     *
     * <p>Almost everything here ships on, because a mod nobody switched on is a mod that appears not
     * to work. The exception is a feature that changes the game rather than adding to it, where a
     * pack should be asked rather than told.
     */
    private static void define(ModConfigSpec.Builder builder, String feature, boolean defaultValue,
                               String... comment) {
        DEFAULTS.put(feature, defaultValue);
        FEATURES.put(feature, builder.comment(comment).define(feature, defaultValue));
    }

    /** Every feature id, in file order. Used by the completeness test rather than by gameplay. */
    public static Set<String> features() {
        return Collections.unmodifiableSet(FEATURES.keySet());
    }

    /** What a feature answers with no config loaded. Used by the test that pins the two together. */
    public static boolean defaultOf(String feature) {
        Boolean value = DEFAULTS.get(feature);
        if (value == null) {
            throw new IllegalArgumentException(feature + " is not a feature of this mod");
        }
        return value;
    }

    public static boolean isFeature(String feature) {
        return FEATURES.containsKey(feature);
    }

    /**
     * Whether a feature is on.
     *
     * <p><b>Unloaded reads as the feature's DEFAULT, deliberately.</b> The config is loaded well before anything here
     * is asked, but "well before" is not "always": a JUnit test running against a mod context with
     * no config file is a real case, and so is any call that ends up earlier in startup than
     * expected. The two ways to be wrong are to throw, which turns a missing file into a crash, or
     * to answer false, which turns it into a mod that silently does nothing and looks like it failed
     * to install. Answering with the shipped default is the only one of the three that is merely
     * uninteresting when it happens - and it has to be the feature's OWN default, not a blanket
     * yes, or a feature that ships off is on wherever nothing is loaded.
     */
    public static boolean enabled(String feature) {
        if (!SPEC.isLoaded()) {
            return DEFAULTS.getOrDefault(feature, Boolean.TRUE);
        }
        return switchFor(feature).get();
    }

    /**
     * The switch itself, rather than its current answer.
     *
     * <p>This exists so the tests can flip one. <b>A config switch that is never flipped in a test
     * is a switch nobody has checked</b> - the gate could be reading the wrong feature, or be on a
     * path that never runs, and every test would still pass because every test runs with everything
     * on. Gameplay code should call {@link #enabled} and never this.
     *
     * @throws IllegalArgumentException if the feature does not exist
     */
    public static ModConfigSpec.BooleanValue switchFor(String feature) {
        ModConfigSpec.BooleanValue value = FEATURES.get(feature);
        if (value == null) {
            throw new IllegalArgumentException(
                feature + " is not a feature of this mod; known features are " + FEATURES.keySet());
        }
        return value;
    }

    public static boolean playerPressurePlates() {
        return enabled(PLAYER_PRESSURE_PLATES);
    }

    public static boolean enchantedGoldenApple() {
        return enabled(ENCHANTED_GOLDEN_APPLE);
    }

    public static boolean toolSlots() {
        return enabled(TOOL_SLOTS);
    }

    /**
     * <b>Implies {@link #toolSlots()}.</b> The swap takes a tool out of a tool slot and puts it
     * back, so with the slots off there is nowhere for it to swap from. Reading the dependency here
     * rather than asking every caller to remember it is what stops a half-on state existing at all.
     */
    public static boolean toolAutoSwap() {
        return toolSlots() && enabled(TOOL_AUTO_SWAP);
    }
}
