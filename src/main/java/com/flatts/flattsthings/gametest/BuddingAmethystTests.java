package com.flatts.flattsthings.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Silk touch picks up budding amethyst; anything else still gets nothing.
 *
 * <p><b>Both halves matter.</b> Vanilla drops nothing from budding amethyst with any tool, and the
 * reason is deliberate - an unobtainable budding block is what stops an amethyst farm being picked
 * up and moved. So the feature is only correct if it is narrow: a test that only proves silk touch
 * works would pass just as well against a change that made every pickaxe drop it, which is a much
 * bigger change to the game than the one asked for.
 *
 * <p>Run through {@code Block.getDrops}, which rolls the real loot table against a real level, so
 * this exercises the global loot modifier rather than reading the JSON back.
 */
final class BuddingAmethystTests {

    private static final BlockPos TARGET = new BlockPos(1, 1, 1);

    private BuddingAmethystTests() {
    }

    private static List<ItemStack> mine(net.minecraft.gametest.framework.GameTestHelper helper,
                                        ItemStack tool) {
        helper.setBlock(TARGET, Blocks.BUDDING_AMETHYST);
        BlockState state = helper.getBlockState(TARGET);
        return Block.getDrops(state, helper.getLevel(), helper.absolutePos(TARGET), null, null, tool);
    }

    private static ItemStack silkTouch(net.minecraft.gametest.framework.GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(helper.getLevel().registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH), 1);
        return pickaxe;
    }

    static void register() {
        FTGameTests.test("silk_touch_picks_up_budding_amethyst", 20, helper -> {
            List<ItemStack> drops = mine(helper, silkTouch(helper));
            helper.assertTrue(drops.stream().anyMatch(s -> s.is(Items.BUDDING_AMETHYST)),
                "silk touch should drop the budding amethyst, dropped " + drops);
            helper.succeed();
        });

        // THE NARROW HALF. Without this, making every tool drop it would pass the test above.
        FTGameTests.test("an_ordinary_pickaxe_still_gets_nothing", 20, helper -> {
            List<ItemStack> drops = mine(helper, new ItemStack(Items.NETHERITE_PICKAXE));
            helper.assertTrue(drops.isEmpty(),
                "without silk touch budding amethyst must still drop nothing, dropped " + drops);
            helper.succeed();
        });

        // And the modifier must not leak onto other tables. neoforge:add_table applies globally and
        // is narrowed by a loot_table_id condition, so a wrong or missing id would quietly add
        // budding amethyst to something else - most likely every block in the game.
        FTGameTests.test("the_modifier_does_not_leak_onto_other_blocks", 20, helper -> {
            helper.setBlock(TARGET, Blocks.STONE);
            List<ItemStack> drops = Block.getDrops(helper.getBlockState(TARGET), helper.getLevel(),
                helper.absolutePos(TARGET), null, null, silkTouch(helper));
            helper.assertFalse(drops.stream().anyMatch(s -> s.is(Items.BUDDING_AMETHYST)),
                "silk-touching stone must not drop budding amethyst, dropped " + drops);
            helper.succeed();
        });
    }
}
