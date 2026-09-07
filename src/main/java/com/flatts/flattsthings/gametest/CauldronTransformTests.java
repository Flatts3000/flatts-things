package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.config.FTConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dipping a stack in a water cauldron, and the accounting around it.
 *
 * <p>Driven through {@code ServerPlayerGameMode.useItemOn}, the method vanilla calls when a
 * right-click packet arrives, rather than by calling the handler. That matters more than usual here:
 * the interaction is reached through vanilla's own {@code CauldronInteraction.Dispatcher}, so calling
 * the handler directly would prove the transformation works while proving nothing about whether the
 * cauldron ever asks for it - and a wrong tag or a wrong dispatcher id breaks exactly that half.
 */
final class CauldronTransformTests {

    private static final BlockPos CAULDRON = new BlockPos(1, 1, 1);

    private CauldronTransformTests() {
    }

    /**
     * Stop the held stack being PLACED when the cauldron declines it.
     *
     * <p>Vanilla's own fall-through: when no cauldron interaction claims the click,
     * {@code useItemOn} carries on to the item, and a block item placed against the cauldron's top
     * face lands in the space above it. That is vanilla behaviour and not this feature's business,
     * but it eats one item and makes "the stack is untouched" read false for the wrong reason.
     * Filling that space keeps the negative tests asserting what they mean.
     */
    private static void blockThePlacement(GameTestHelper helper) {
        helper.setBlock(CAULDRON.above(), Blocks.STONE);
    }

    private static void fillCauldron(GameTestHelper helper) {
        helper.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState()
            .setValue(LayeredCauldronBlock.LEVEL, 3));
    }

    /**
     * A survival player holding {@code held}.
     *
     * <p>SURVIVAL explicitly: a creative player's {@code Inventory.add} reports success without
     * storing anything, so every count this file asserts would read zero for a reason that has
     * nothing to do with cauldrons.
     */
    private static ServerPlayer holding(GameTestHelper helper, ItemStack held) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), held);
        return player;
    }

    private static void rightClick(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(CAULDRON);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(),
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static int countOf(ServerPlayer player, Item item) {
        int total = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    static void register() {
        // THE CASE THAT EARNS THE FEATURE. Vanilla will not set concrete powder against a cauldron,
        // because ConcretePowderBlock.canSolidify wants a real water FluidState and a cauldron has
        // none. Read in the 26.1 sources rather than assumed.
        FTGameTests.test("concrete_powder_sets_in_a_water_cauldron", 20, helper -> {
            fillCauldron(helper);
            ServerPlayer player = holding(helper, new ItemStack(Items.WHITE_CONCRETE_POWDER, 64));

            rightClick(helper, player);

            helper.assertTrue(countOf(player, Items.WHITE_CONCRETE) == 64,
                "the whole stack should have set, found "
                    + countOf(player, Items.WHITE_CONCRETE) + " concrete");
            helper.assertTrue(countOf(player, Items.WHITE_CONCRETE_POWDER) == 0,
                "and no powder should be left, found "
                    + countOf(player, Items.WHITE_CONCRETE_POWDER));
            helper.succeed();
        });

        // Mud is the bulk form of something vanilla already does with a water bottle, so it is a
        // second ENTRY rather than a second special case: same tag, same data map, no extra code.
        FTGameTests.test("dirt_becomes_mud_in_a_water_cauldron", 20, helper -> {
            fillCauldron(helper);
            ServerPlayer player = holding(helper, new ItemStack(Items.DIRT, 8));

            rightClick(helper, player);

            helper.assertTrue(countOf(player, Items.MUD) == 8,
                "eight dirt should be eight mud, found " + countOf(player, Items.MUD));
            helper.succeed();
        });

        // THE PRICE IS THE POINT. A cauldron that converted for free would make a water source
        // strictly worse than a cauldron; this is meant to remove tedium, not cost.
        FTGameTests.test("a_transform_drinks_a_level_of_the_cauldron", 20, helper -> {
            fillCauldron(helper);
            ServerPlayer player = holding(helper, new ItemStack(Items.DIRT, 1));

            rightClick(helper, player);

            helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 2);
            helper.succeed();
        });

        // AND A DRY CAULDRON IS JUST A CAULDRON - for a better reason than first written here. The
        // comment claimed this pinned the water-level check; it does not, and driving it red proved
        // so. An EMPTY cauldron is a different block with a different dispatcher (vanilla keeps one
        // per fill state), so this interaction is never consulted at all and the level check is
        // never reached. What this does pin is that the feature is registered to the WATER
        // dispatcher only: registering it to all of them, which the NeoForge event makes a one-word
        // change, would make a dry cauldron transform things out of nothing.
        FTGameTests.test("an_empty_cauldron_transforms_nothing", 20, helper -> {
            helper.setBlock(CAULDRON, Blocks.CAULDRON.defaultBlockState());
            blockThePlacement(helper);
            ServerPlayer player = holding(helper, new ItemStack(Items.DIRT, 4));

            rightClick(helper, player);

            helper.assertTrue(countOf(player, Items.MUD) == 0,
                "an empty cauldron should not have made mud, found " + countOf(player, Items.MUD));
            helper.assertTrue(countOf(player, Items.DIRT) == 4, "and the dirt should be untouched");
            helper.succeed();
        });

        // THE NEGATIVE, without which a handler that ignored its input would pass everything above.
        FTGameTests.test("an_item_with_no_transform_is_left_alone", 20, helper -> {
            fillCauldron(helper);
            blockThePlacement(helper);
            ServerPlayer player = holding(helper, new ItemStack(Items.COBBLESTONE, 16));

            rightClick(helper, player);

            helper.assertTrue(countOf(player, Items.COBBLESTONE) == 16,
                "cobblestone is not in the tag and should be untouched, found "
                    + countOf(player, Items.COBBLESTONE));
            helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 3);
            helper.succeed();
        });

        // VANILLA CAULDRON USES MUST SURVIVE THIS. The feature only ADDS entries to the water
        // dispatcher, and the way to prove that is the vanilla interaction sharing this cauldron:
        // filling a bottle still works and still costs a level.
        FTGameTests.test("filling_a_bottle_from_the_cauldron_still_works", 20, helper -> {
            fillCauldron(helper);
            ServerPlayer player = holding(helper, new ItemStack(Items.GLASS_BOTTLE, 1));

            rightClick(helper, player);

            helper.assertTrue(countOf(player, Items.POTION) == 1,
                "vanilla's bottle filling should be untouched, found "
                    + countOf(player, Items.POTION) + " potions");
            helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 2);
            helper.succeed();
        });

        // THE SWITCH, in an environment of its own because the config is one global value and these
        // tests run beside each other.
        FTGameTests.test("switching_cauldron_transforms_off_gives_a_vanilla_cauldron", 20,
            FTGameTests.aloneIn("switching_cauldron_transforms_off_gives_a_vanilla_cauldron"),
            helper -> {
                fillCauldron(helper);
                blockThePlacement(helper);
                ServerPlayer player = holding(helper,
                    new ItemStack(Items.WHITE_CONCRETE_POWDER, 4));
                try {
                    FTConfig.switchFor(FTConfig.CAULDRON_TRANSFORMS).set(false);

                    rightClick(helper, player);

                    helper.assertTrue(countOf(player, Items.WHITE_CONCRETE) == 0,
                        "with the feature off nothing should have set, found "
                            + countOf(player, Items.WHITE_CONCRETE));
                    helper.assertTrue(countOf(player, Items.WHITE_CONCRETE_POWDER) == 4,
                        "and the powder should be untouched");
                    helper.assertBlockProperty(CAULDRON, LayeredCauldronBlock.LEVEL, 3);
                } finally {
                    FTConfig.switchFor(FTConfig.CAULDRON_TRANSFORMS).set(true);
                }
                helper.succeed();
            });
    }
}
