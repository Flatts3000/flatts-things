package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.registry.FTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Player Pressure Plate: what presses it, and what must not.
 *
 * <p><b>These drive the game's own entry point rather than waiting for a walk.</b> A plate is
 * pressed from {@code BlockStateBase.entityInside}, which the game calls out of
 * {@code Entity.checkInsideBlocks} during movement. A mock server player is not driven by client
 * packets and a {@code spawnWithNoFreeWill} mob does not walk, so waiting for either to wander onto
 * a plate would be a test that passes by timing out. Calling {@code entityInside} directly with a
 * real entity, positioned in a real level, exercises the same method with none of the timing.
 *
 * <p><b>Every negative here is paired with a vanilla control.</b> "A cow does not press it" is a
 * claim that can pass for the wrong reason - if the cow were never actually placed in the plate's
 * box, nothing would press it and the test would still be green. Each control asserts the vanilla
 * plate of the equivalent tier DOES fire on the very same entity, so a positioning mistake fails
 * the control instead of quietly passing the feature.
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

    static void register() {
        // THE FEATURE.
        FTGameTests.test("a_player_presses_the_player_plate", 20, helper -> {
            placePlate(helper, FTBlocks.PLAYER_PRESSURE_PLATE.get());
            stepOn(helper, survivalPlayer(helper));
            helper.assertTrue(isPowered(helper),
                "a player standing on the player plate must power it");
            helper.succeed();
        });

        // THE POINT OF THE BLOCK: a mob is what a vanilla stone plate cannot ignore.
        FTGameTests.test("a_mob_does_not_press_the_player_plate", 20, helper -> {
            placePlate(helper, FTBlocks.PLAYER_PRESSURE_PLATE.get());
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
            placePlate(helper, FTBlocks.PLAYER_PRESSURE_PLATE.get());
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
            placePlate(helper, FTBlocks.PLAYER_PRESSURE_PLATE.get());
            ServerPlayer player = survivalPlayer(helper);
            stepOn(helper, player);
            helper.assertTrue(isPowered(helper), "precondition: the plate must be pressed first");

            BlockPos abs = helper.absolutePos(PLATE);
            player.setPos(abs.getX() + 0.5, abs.getY() + 8.0, abs.getZ() + 0.5);
            helper.getBlockState(PLATE).tick(
                helper.getLevel(), abs, helper.getLevel().getRandom());

            helper.assertFalse(isPowered(helper),
                "the plate must release once the player is out of its box");
            helper.succeed();
        });
    }
}
