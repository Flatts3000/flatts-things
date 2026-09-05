package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTBlocks.PlateVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The player pressure plates: what presses them, and what must not.
 *
 * <p><b>These drive the game's own entry point rather than waiting for a walk.</b> A plate is
 * pressed from {@code BlockStateBase.entityInside}, which the game calls out of
 * {@code Entity.checkInsideBlocks} during movement. A mock server player is not driven by client
 * packets and a {@code spawnWithNoFreeWill} mob does not wander, so waiting for either to step onto
 * a plate would be a test that passes by timing out. Calling {@code entityInside} directly with a
 * real entity, positioned in a real level, exercises the same method with none of the timing.
 *
 * <p><b>Every negative here is paired with a vanilla control.</b> "A cow does not press it" is a
 * claim that can pass for the wrong reason - if the cow were never actually inside the plate's box,
 * nothing would press anything and the test would still be green. Each control asserts the vanilla
 * plate of the equivalent tier DOES fire on the very same entity, so a positioning mistake fails
 * the control instead of quietly passing the feature.
 *
 * <p>The detailed behaviour tests run against the stone variant alone, because all fourteen share
 * one class and running the same code fourteen times proves nothing new. What IS worth sweeping is
 * the wiring: that every variant was registered against the right class, and that the set is
 * complete against vanilla. Those are the sweeps at the end.
 */
final class PlayerPressurePlateTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos PLATE = new BlockPos(1, 1, 1);

    private PlayerPressurePlateTests() {
    }

    private static void placePlate(GameTestHelper helper, Block plate) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(PLATE, plate);
    }

    /** Put the entity in the plate's space and run the press check the game would run. */
    private static void stepOn(GameTestHelper helper, Entity entity) {
        BlockPos abs = helper.absolutePos(PLATE);
        entity.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        helper.getBlockState(PLATE).entityInside(
            helper.getLevel(), abs, entity, InsideBlockEffectApplier.NOOP, true);
    }

    /** Move an entity well clear of the plate's box, so the next check starts clean. */
    private static void stepOff(GameTestHelper helper, Entity entity) {
        BlockPos abs = helper.absolutePos(PLATE);
        entity.setPos(abs.getX() + 0.5, abs.getY() + 8.0, abs.getZ() + 0.5);
    }

    private static boolean isPowered(GameTestHelper helper) {
        return helper.getBlockState(PLATE).getValue(BlockStateProperties.POWERED);
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // makeMockServerPlayerInLevel does NOT default to survival - it comes up in creative, whose
        // abilities carry instabuild. Nothing in this block reads that today, but a plate that
        // behaved differently for a creative player would be invisible here without this line.
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Block stonePlate() {
        return FTBlocks.plate("stone").get();
    }

    /** The {@code minecraft:}-namespaced tags a block is in, as strings, for set comparison. */
    private static Set<String> vanillaTagsOf(Block block) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block).tags()
            .map(tag -> tag.location().toString())
            .filter(id -> id.startsWith("minecraft:"))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Fail once with the whole list, rather than on whichever entry happened to be walked first. */
    private static void report(GameTestHelper helper, List<String> problems, String what) {
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " " + what + ": " + String.join(", ", problems));
        }
        helper.succeed();
    }

    static void register() {
        // THE FEATURE.
        FTGameTests.test("a_player_presses_the_player_plate", 20, helper -> {
            placePlate(helper, stonePlate());
            stepOn(helper, survivalPlayer(helper));
            helper.assertTrue(isPowered(helper),
                "a player standing on the player plate must power it");
            helper.succeed();
        });

        // THE POINT OF THE BLOCK: a mob is what a vanilla stone plate cannot ignore.
        FTGameTests.test("a_mob_does_not_press_the_player_plate", 20, helper -> {
            placePlate(helper, stonePlate());
            Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, PLATE);
            stepOn(helper, cow);
            helper.assertFalse(isPowered(helper),
                "a cow must not power the player plate - that is the whole reason it exists");
            helper.succeed();
        });

        // CONTROL for the test above. If the cow is not really in the plate's box, this goes red
        // instead of the negative going green for free.
        FTGameTests.test("control_a_stone_plate_does_react_to_the_same_mob", 20, helper -> {
            placePlate(helper, Blocks.STONE_PRESSURE_PLATE);
            Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, PLATE);
            stepOn(helper, cow);
            helper.assertTrue(isPowered(helper),
                "control: vanilla stone reacts to mobs, so this cow is positioned to be seen");
            helper.succeed();
        });

        // A dropped item is what a vanilla WOODEN plate cannot ignore.
        FTGameTests.test("a_dropped_item_does_not_press_the_player_plate", 20, helper -> {
            placePlate(helper, stonePlate());
            ItemEntity dropped = helper.spawnItem(Items.STONE, PLATE);
            stepOn(helper, dropped);
            helper.assertFalse(isPowered(helper), "a dropped item must not power the player plate");
            helper.succeed();
        });

        // CONTROL for the test above.
        FTGameTests.test("control_an_oak_plate_does_react_to_the_same_item", 20, helper -> {
            placePlate(helper, Blocks.OAK_PRESSURE_PLATE);
            ItemEntity dropped = helper.spawnItem(Items.STONE, PLATE);
            stepOn(helper, dropped);
            helper.assertTrue(isPowered(helper),
                "control: vanilla oak reacts to any entity, so this item is positioned to be seen");
            helper.succeed();
        });

        // THE RELEASE PATH. A plate that latches on forever is a worse bug than one that never
        // fires, because a redstone build keeps working until the player steps off. Pressing
        // schedules a tick; that tick is what lets go.
        FTGameTests.test("the_player_plate_releases_when_the_player_leaves", 20, helper -> {
            placePlate(helper, stonePlate());
            ServerPlayer player = survivalPlayer(helper);
            stepOn(helper, player);
            helper.assertTrue(isPowered(helper), "precondition: the plate must be pressed first");

            stepOff(helper, player);
            helper.getBlockState(PLATE).tick(
                helper.getLevel(), helper.absolutePos(PLATE), helper.getLevel().getRandom());

            helper.assertFalse(isPowered(helper),
                "the plate must release once the player is out of its box");
            helper.succeed();
        });

        // THE WIRING SWEEP. All fourteen share one class, so this is not re-proving the behaviour;
        // it is proving each variant was registered as a PlayerPressurePlateBlock at all. One
        // variant wired to the vanilla class by mistake would fire for cows and still pass every
        // test above, because every test above only ever places the stone one.
        FTGameTests.test("every_variant_presses_for_a_player_and_ignores_a_mob", 100, helper -> {
            ServerPlayer player = survivalPlayer(helper);
            Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, PLATE);
            stepOff(helper, cow);

            List<String> problems = new ArrayList<>();
            for (PlateVariant variant : FTBlocks.VARIANTS) {
                Block plate = FTBlocks.plate(variant.material()).get();

                placePlate(helper, plate);
                stepOn(helper, player);
                if (!isPowered(helper)) {
                    problems.add(variant.blockId() + " (ignored a player)");
                }
                stepOff(helper, player);

                placePlate(helper, plate);
                stepOn(helper, cow);
                if (isPowered(helper)) {
                    problems.add(variant.blockId() + " (fired for a cow)");
                }
                stepOff(helper, cow);
            }
            report(helper, problems, "variants with the wrong sensitivity");
        });

        // COMPLETENESS AGAINST VANILLA, walked off the registry rather than off the list in
        // FTBlocks, so a wood type added by a future Minecraft version fails this instead of being
        // quietly missed. WeightedPressurePlateBlock is NOT a PressurePlateBlock - both extend the
        // abstract parent - so the two weighted plates fall out of the filter on their own rather
        // than needing an exemption list.
        FTGameTests.test("every_vanilla_pressure_plate_has_a_player_counterpart", 20, helper -> {
            List<String> missing = new ArrayList<>();
            int seen = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if (!id.getNamespace().equals("minecraft") || !(block instanceof PressurePlateBlock)) {
                    continue;
                }
                seen++;
                String material = id.getPath().replace("_pressure_plate", "");
                Identifier expected = Identifier.fromNamespaceAndPath(
                    FlattsThings.MOD_ID, material + "_player_pressure_plate");
                if (!BuiltInRegistries.BLOCK.containsKey(expected)) {
                    missing.add(expected.toString());
                }
            }
            helper.assertTrue(seen >= 14,
                "only " + seen + " vanilla pressure plates were found - the walk is broken, so this "
                    + "would pass without checking anything");
            report(helper, missing, "vanilla pressure plates with no player counterpart");
        });

        // Each variant is meant to be its vanilla counterpart in every respect but sensitivity,
        // which is why properties are copied wholesale instead of restated. This proves the copy
        // actually happened, and it catches a variant pointed at the WRONG vanilla block - a
        // mistake that would leave warped plates flammable and otherwise look like nothing.
        //
        // THE EXPECTED BLOCK IS RESOLVED FROM THE MATERIAL NAME, NOT FROM variant.vanilla(), and
        // that distinction is the entire test. The first version of this compared each plate
        // against the very field its properties were copied from, so the two agreed by
        // construction: pointing "warped" at OAK_PRESSURE_PLATE changed both sides at once and the
        // test stayed green. It was found by trying exactly that. A test whose expectation comes
        // from the thing under test is not a test.
        FTGameTests.test("every_variant_matches_its_vanilla_block_properties", 20, helper -> {
            List<String> problems = new ArrayList<>();
            for (PlateVariant variant : FTBlocks.VARIANTS) {
                Block ours = FTBlocks.plate(variant.material()).get();
                Block theirs = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(
                    "minecraft", variant.material() + "_pressure_plate"));
                if (theirs == null) {
                    problems.add(variant.blockId() + " (no vanilla plate for this material)");
                    continue;
                }

                if (ours.defaultDestroyTime() != theirs.defaultDestroyTime()) {
                    problems.add(variant.blockId() + " (hardness)");
                }
                if (ours.defaultMapColor() != theirs.defaultMapColor()) {
                    problems.add(variant.blockId() + " (map colour)");
                }
                if (ours.defaultBlockState().ignitedByLava()
                        != theirs.defaultBlockState().ignitedByLava()) {
                    problems.add(variant.blockId() + " (flammability)");
                }
            }
            report(helper, problems, "variants that drifted from their vanilla counterpart");
        });

        // A FAKE PLAYER DOES NOT PRESS IT, AND THE BLOCK DOES NOTHING TO ARRANGE THAT (ruling
        // 2026-09-05, issue #7).
        //
        // The issue was filed asserting the opposite, on the reasoning that a fake player IS a
        // Player and passes both of getEntityCount's filters. That reasoning is sound and the
        // conclusion is still wrong, which is why this test exists rather than a paragraph.
        //
        // A NeoForge FakePlayer is never added to the level. It is a detached ServerPlayer used to
        // carry identity and permissions, so `level.getEntitiesOfClass(Player.class, ...)` cannot
        // find it however precisely it is positioned. The plate asks the world what is standing on
        // it, and a fake player is not standing anywhere.
        //
        // So this is a CONSEQUENCE, not a guarantee. A mod that genuinely spawns a Player entity
        // into the world would press this plate, and nothing here prevents that. Pinning the
        // observed behaviour is worth more than pretending to a rule the block does not enforce.
        FTGameTests.test("a_fake_player_does_not_press_the_player_plate", 20, helper -> {
            placePlate(helper, stonePlate());
            ServerPlayer fake = FakePlayerFactory.get(
                helper.getLevel(),
                new com.mojang.authlib.GameProfile(
                    java.util.UUID.nameUUIDFromBytes("flattsthings-test".getBytes()),
                    "flattsthings-test"));
            stepOn(helper, fake);
            helper.assertFalse(isPowered(helper),
                "a NeoForge fake player is not a world entity, so the plate cannot see it");
            helper.succeed();
        });

        // CONTROL for the test above: a REAL player in the same position does press it, so the
        // negative cannot pass merely because stepOn did nothing.
        FTGameTests.test("control_a_real_player_in_that_position_does_press_it", 20, helper -> {
            placePlate(helper, stonePlate());
            stepOn(helper, survivalPlayer(helper));
            helper.assertTrue(isPowered(helper),
                "control: a real player at the same spot presses it, so the fake-player negative "
                    + "is about the entity and not about the positioning");
            helper.succeed();
        });

        // TAG PARITY, and it is mining behaviour rather than bookkeeping. A wooden plate is
        // axe-mineable only because #minecraft:wooden_pressure_plates sits inside
        // #minecraft:mineable/axe; miss that tag and the variant is still breakable, just at the
        // wrong speed with the wrong tool, which no other test here would notice.
        //
        // It compares the SETS rather than checking a list of tags written down twice, so a tag
        // vanilla adds to its plates in a future version fails this instead of being missed. Only
        // minecraft-namespaced tags are compared: this mod is free to add tags of its own, and
        // neither side should be forced to mirror the other's namespace.
        FTGameTests.test("every_variant_is_in_the_same_vanilla_tags_as_its_counterpart", 20, helper -> {
            List<String> problems = new ArrayList<>();
            for (PlateVariant variant : FTBlocks.VARIANTS) {
                Block ours = FTBlocks.plate(variant.material()).get();
                Block theirs = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(
                    "minecraft", variant.material() + "_pressure_plate"));
                if (theirs == null) {
                    problems.add(variant.blockId() + " (no vanilla plate for this material)");
                    continue;
                }

                Set<String> oursTags = vanillaTagsOf(ours);
                Set<String> theirsTags = vanillaTagsOf(theirs);
                if (theirsTags.isEmpty()) {
                    problems.add(variant.blockId() + " (the vanilla plate reported no tags, so this "
                        + "comparison proves nothing)");
                    continue;
                }
                for (String tag : theirsTags) {
                    if (!oursTags.contains(tag)) {
                        problems.add(variant.blockId() + " (missing " + tag + ")");
                    }
                }
                for (String tag : oursTags) {
                    if (!theirsTags.contains(tag)) {
                        problems.add(variant.blockId() + " (extra " + tag + ")");
                    }
                }
            }
            report(helper, problems, "variants whose vanilla tags differ from their counterpart");
        });
    }
}
