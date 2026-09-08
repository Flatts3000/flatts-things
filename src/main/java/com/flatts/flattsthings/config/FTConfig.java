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

    /**
     * Dipping a stack in a water cauldron to transform it: concrete powder sets, dirt becomes mud.
     *
     * <p>Off leaves the cauldron doing exactly what vanilla's does, which is the test a switch like
     * this has to pass: a pack that turns it off gets its dye washing and bottle filling back
     * untouched. That holds because nothing this mod puts in the tag has a vanilla cauldron use of
     * its own - NOT because the registration is additive, which it is not. See
     * {@code CauldronTransforms} for what a tag entry actually does to the item it names.
     */
    public static final String CAULDRON_TRANSFORMS = "cauldron_transforms";

    /**
     * Combining a chestplate and an elytra so one chest slot does both jobs.
     *
     * <p><b>This one changes the game rather than filling a gap in it</b>, and the switch matters
     * more here than elsewhere. The chestplate-or-elytra choice is a cost Mojang has kept
     * deliberately for years; this removes it. Off leaves that choice exactly where vanilla put it.
     */
    public static final String ARMORED_ELYTRA = "armored_elytra";

    /**
     * The woodcutter: a saw bench for wood.
     *
     * <p>Logs, bark blocks and their stripped forms cut into planks; a log strips or barks; a
     * stripped log becomes a shelf; planks become stairs, slabs, buttons or sticks. Every rate is a
     * crafting bench's own, except stairs, which are a third cheaper the way a stonecutter's are.
     *
     * <p>Off removes the block from the creative tab, its crafting recipe, and all the cuts. Because
     * those are recipes rather than behaviour, the switch has to act in data - there is no runtime
     * call that unloads one, and a recipe left loaded would still show in the recipe book and in JEI.
     *
     * <p><b>This is the one feature where "placed blocks keep working" does not fully hold</b>, and
     * the exception is recorded in CLAUDE.md beside the rule. A woodcutter already placed stays
     * placed, breakable and unchanged - but its whole purpose is the cuts, and those are gone, so it
     * opens a menu that offers nothing. Nothing is deleted and turning the switch back on restores
     * it completely.
     */
    public static final String WOOD_CUTTING = "wood_cutting";

    /**
     * Terrain slabs: half blocks of dirt, gravel, sand and the rest of the ground you walk on.
     *
     * <p>Vanilla gives slabs to the things you build WITH and none to the things you build ON, which
     * is less a principle than where Mojang stopped. Eleven families are covered.
     *
     * <p><b>They behave, rather than merely looking right.</b> Gravel and sand fall and land the
     * right way up, podzol takes the snowy side under a snow layer, rooted dirt grows hanging roots
     * when bonemealed, and mud is two pixels short so you sink into it. Mining one gives half of
     * what the whole block gives: a podzol slab yields a dirt slab and clay yields two clay balls.
     *
     * <p>Off removes the blocks from the creative tab and their recipes. Placed slabs keep working,
     * which is the ordinary reading of this switch rather than the woodcutter's exception: a slab's
     * behaviour is its own rather than a recipe list, so nothing goes quiet.
     */
    public static final String TERRAIN_SLABS = "terrain_slabs";

    /**
     * Powered rails from copper: the same recipe with copper where the gold goes, for half as many.
     *
     * <p><b>One recipe file and nothing else</b> (owner, 2026-09-08). The issue proposed a copper
     * rail BLOCK that oxidised through four stages and pushed a cart less as it weathered, which
     * would have meant a new block class - {@code PoweredRailBlock} hardcodes its chaining distance
     * and applies its boost in its own logic, so a rail with different strength cannot be data. The
     * ruling cut all of it: no new block, no oxidation, no weaker push. Vanilla's powered rail, made
     * out of copper.
     *
     * <p><b>No accessor, deliberately.</b> Nothing in Java asks about this feature: the switch acts
     * entirely in data, through the {@code flattsthings:feature_enabled} condition on the recipe,
     * which resolves the id as a string. {@code gravel_to_flint} and {@code silk_touch_budding_amethyst}
     * are the same shape and ship none either. A method nobody calls would also be an uncovered line
     * in a package the merged coverage gate measures.
     *
     * <p><b>Three rails rather than six, and that number is the whole balance of it.</b> A straight
     * substitution would leave the gold recipe with nothing to offer - copper is the metal vanilla
     * gives players tonnes of - so the cheaper ingredient buys fewer rails and gold stays the
     * efficient route for anybody who has it. The owner delegated the number; it is one digit in one
     * file if it wants changing.
     */
    public static final String COPPER_POWERED_RAILS = "copper_powered_rails";

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

        define(builder, CAULDRON_TRANSFORMS,
            "Dip a stack in a water cauldron to transform it: concrete powder sets to concrete, and",
            "dirt becomes mud. Both already work in vanilla against a water SOURCE block or a water",
            "bottle; the cauldron is the bulk version, and costs one of the cauldron's three levels",
            "per stack. Off gives you an ordinary vanilla cauldron back - washing dye and filling",
            "bottles are untouched either way.");

        define(builder, ARMORED_ELYTRA,
            "Combine a chestplate and an elytra on an anvil: the chestplate keeps its armour, its",
            "enchantments and its trim, and gains the elytra's gliding. One chest slot does both",
            "jobs. The elytra is consumed - ALONG WITH ANY ENCHANTMENTS ON IT, which are not carried",
            "across - and flight then wears the CHESTPLATE, so the armour you are relying on is the",
            "thing being worn down.",
            "",
            "Note this removes a trade vanilla has kept on purpose for years. On by default anyway,",
            "for the same reason silk touch on budding amethyst is: a mod nobody switched on is a",
            "mod that appears not to work, and a pack that wants the vanilla choice back has this",
            "line to turn off. That is the deal every switch here offers, and it is worth more than",
            "guessing which features a pack would have wanted.");

        define(builder, WOOD_CUTTING,
            "The woodcutter: a saw bench for wood. The game has a cutter for stone and none for",
            "wood.",
            "",
            "Logs, bark blocks and their stripped forms cut into planks. A log strips, or turns to",
            "bark. A stripped log becomes a shelf. Planks become stairs, slabs, buttons or sticks.",
            "Every rate is the crafting bench's own, except stairs, which are a third cheaper - the",
            "same saving a stonecutter already gives on stone stairs.",
            "",
            "Doors, trapdoors, fences, signs and boats are deliberately NOT on it: a cut takes one",
            "item, so anything costing more than a plank on a bench would come out two to five",
            "times cheaper here.",
            "",
            "Off removes the block from the creative tab, its recipe, and every cut. A woodcutter",
            "already placed stays where it is and can still be broken and picked up, but it has",
            "nothing left to offer until this is turned back on - unlike the other switches here,",
            "which leave placed blocks fully working.");

        define(builder, COPPER_POWERED_RAILS,
            "Powered rails from copper: the same recipe as the gold one, with copper where the gold",
            "goes, for three rails instead of six.",
            "",
            "Vanilla gates the only rail that accelerates behind six gold per six rails, which is",
            "why early rail travel is mostly pushing. Copper is the metal the game gives you tonnes",
            "of and almost nothing to do with.",
            "",
            "Half the yield is the point rather than an oversight: a straight swap would leave the",
            "gold recipe with nothing to offer at all. This way copper buys rails early and gold",
            "stays worth using once you have it.",
            "",
            "It makes the ORDINARY powered rail. There is no copper rail block, nothing oxidises,",
            "and a rail made this way is indistinguishable from one made with gold.",
            "",
            "Off removes the recipe. Rails already crafted are ordinary powered rails and are",
            "untouched.");

        define(builder, TERRAIN_SLABS,
            "Terrain slabs: half blocks of the ground rather than of the things you build with.",
            "Vanilla gives stairs and slabs to bricks and planks and none to dirt, gravel or sand.",
            "",
            "Eleven families: dirt, grass, mycelium, coarse dirt, rooted dirt, podzol, mud, clay,",
            "gravel, sand and red sand. Three blocks in a row give six slabs, vanilla's own rate for",
            "slab it ships. Gravel is the one exception, at six for twelve: three gravel in a row is",
            "already this pack's flint recipe, and that recipe is shapeless, so it matches any",
            "arrangement of three and no three-gravel slab recipe could coexist with it.",
            "",
            "They behave rather than only looking right. Gravel and sand fall, and a top slab lands",
            "as a bottom one instead of floating. Podzol takes the snowy side under snow. Rooted",
            "dirt grows hanging roots when bonemealed, except as a top slab, where they would have",
            "nothing to hang from. Mud is two pixels short, so you sink into it.",
            "",
            "Grass and mycelium spread and die back the way their full blocks do. A dirt slab beside",
            "a grass block greens itself, which is the direction vanilla cannot do on its own - its",
            "grass looks for a dirt BLOCK and will never see a slab. Spreading only crosses between",
            "matching halves: a bottom grass slab does not green a top dirt slab, because they do",
            "not touch. Bonemeal works on a grass slab except as a bottom one, where the plants",
            "would sprout floating half a block above it.",
            "",
            "Mining one gives half of what the whole block gives: a podzol slab yields a dirt slab",
            "and clay yields two clay balls. Flint comes only from a DOUBLE gravel slab, which is a",
            "whole block's worth, so cutting gravel into slabs and recombining it is neutral.",
            "",
            "Flowers, saplings and grass grow on the soil families - dirt, grass, mycelium, coarse",
            "dirt, rooted dirt, podzol and mud - on a TOP or double slab. A bottom slab refuses,",
            "because its surface is half way up its own block and a plant would sprout floating.",
            "",
            "A hoe does NOT till a slab, on any half. Half a block of farmland would have to",
            "become a farmland slab, and there is no such block, so the hoe does nothing at all.",
            "",
            "Off removes them from the creative tab and drops their recipes. Slabs already placed",
            "keep working exactly as they did.");

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

    public static boolean terrainSlabs() {
        return enabled(TERRAIN_SLABS);
    }

    public static boolean woodCutting() {
        return enabled(WOOD_CUTTING);
    }

    public static boolean armoredElytra() {
        return enabled(ARMORED_ELYTRA);
    }

    public static boolean cauldronTransforms() {
        return enabled(CAULDRON_TRANSFORMS);
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
