package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.content.terrain.SnowyTerrainSlabBlock;
import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTBlocks.TerrainSlabVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The terrain slabs, and specifically the parts that are NOT "it is a slab".
 *
 * <p>Vanilla's {@code SlabBlock} is inherited rather than reimplemented, so the half-block half -
 * the type property, the shape, waterlogging, the doubling rule - is Mojang's and is not re-tested
 * here. What is tested is everything this mod had to decide: what a slab of each material drops,
 * that gravel falls and lands the right way up, that podzol's snowy state reads the right half, and
 * that rooted dirt refuses bonemeal where the roots would have nothing to hang from.
 *
 * <p>Every one of those is a place where the obvious implementation is wrong in a way no compile
 * error and no file-existence sweep would catch.
 */
final class TerrainSlabTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos SLAB = new BlockPos(1, 1, 1);
    private static final BlockPos ABOVE = new BlockPos(1, 2, 1);
    private static final BlockPos HIGH = new BlockPos(1, 3, 1);

    private TerrainSlabTests() {
    }

    private static Block slab(String family) {
        return FTBlocks.terrainSlab(family).get();
    }

    private static void report(GameTestHelper helper, List<String> problems, String what) {
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " " + what + ": " + String.join(", ", problems));
        }
        helper.succeed();
    }

    static void register() {
        // Resolved through the real crafting lookup with a real 3x1 grid. A recipe naming an
        // ingredient that does not exist is dropped at load with one log line and no failure
        // anywhere, so file existence proves nothing and this does.
        FTGameTests.test("every_terrain_slab_has_a_recipe_that_actually_crafts", 30, helper -> {
            List<String> problems = new ArrayList<>();
            for (TerrainSlabVariant variant : FTBlocks.TERRAIN_SLABS) {
                ItemStack source = new ItemStack(variant.vanilla());
                // Gravel takes six, because this mod's own gravel-to-flint recipe is shapeless on
                // exactly three and would win the three-gravel grid whatever shape we asked for.
                // Asking in the shape the data actually uses is the point: a test that only ever
                // tried a row would report gravel as having no recipe at all.
                boolean batch = variant.family().equals("gravel");
                CraftingInput input = batch
                    ? CraftingInput.of(3, 2, List.of(source, source, source, source, source, source))
                    : CraftingInput.of(3, 1, List.of(source, source, source));
                int wantCount = batch ? 12 : 6;
                Optional<RecipeHolder<CraftingRecipe>> found = helper.getLevel().getServer()
                    .getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());

                if (found.isEmpty()) {
                    problems.add(variant.blockId() + " (no recipe)");
                    continue;
                }
                ItemStack result = found.get().value().assemble(input);
                if (!result.is(slab(variant.family()).asItem())) {
                    problems.add(variant.blockId() + " crafts " + result.getItem());
                } else if (result.getCount() != wantCount) {
                    problems.add(variant.blockId() + " crafts " + result.getCount()
                        + " not " + wantCount);
                }
            }
            report(helper, problems, "terrain slabs whose recipe does not craft them");
        });

        // HALF OF WHAT THE BLOCK GIVES, which for three of the ten is not the slab itself. A table
        // that simply dropped its own block would pass a file-existence sweep and hand a player a
        // podzol slab from a block that vanilla says yields dirt.
        FTGameTests.test("a_terrain_slab_drops_half_of_what_its_block_does", 40, helper -> {
            List<String> problems = new ArrayList<>();
            record Expected(String family, net.minecraft.world.item.Item item, int count) { }
            List<Expected> cases = List.of(
                new Expected("dirt", slab("dirt").asItem(), 1),
                new Expected("podzol", slab("dirt").asItem(), 1),
                new Expected("clay", Items.CLAY_BALL, 2));

            for (Expected want : cases) {
                helper.setBlock(SLAB, slab(want.family()));
                List<ItemStack> drops = Block.getDrops(helper.getBlockState(SLAB),
                    helper.getLevel(), helper.absolutePos(SLAB), null);
                helper.setBlock(SLAB, Blocks.AIR);

                long matching = drops.stream()
                    .filter(stack -> stack.is(want.item()))
                    .mapToLong(ItemStack::getCount).sum();
                if (matching != want.count()) {
                    problems.add(want.family() + " gave " + drops + ", wanted " + want.count()
                        + " " + want.item());
                }
            }
            report(helper, problems, "terrain slabs dropping the wrong thing");
        });

        FTGameTests.test("a_gravel_slab_falls_when_nothing_holds_it_up", 80, helper -> {
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(SLAB, Blocks.AIR);
            helper.setBlock(ABOVE, Blocks.AIR);
            helper.setBlock(HIGH, slab("gravel"));
            helper.succeedWhen(() -> {
                helper.assertBlockPresent(slab("gravel"), SLAB);
                helper.assertBlockPresent(Blocks.AIR, HIGH);
            });
        });

        // THE ONE PLACE THIS DELIBERATELY DIFFERS FROM A STRAIGHT COPY OF FallingBlock.
        // FallingBlockEntity carries the state it was handed and places it unchanged, so a TOP slab
        // knocked loose would land as a top slab: half a block of gravel resting on nothing with a
        // gap underneath. Nothing else in the game does that, so it is normalised on the way down.
        FTGameTests.test("a_falling_top_slab_lands_as_a_bottom_slab", 80, helper -> {
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(SLAB, Blocks.AIR);
            helper.setBlock(ABOVE, Blocks.AIR);
            helper.setBlock(HIGH, slab("gravel").defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP));
            helper.succeedWhen(() -> {
                BlockState landed = helper.getBlockState(SLAB);
                helper.assertBlockPresent(slab("gravel"), SLAB);
                if (landed.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
                    helper.fail("a top slab landed as " + landed.getValue(SlabBlock.TYPE)
                        + ", which leaves it floating with a gap underneath");
                }
            });
        });

        // WHAT HAPPENS WHEN IT LANDS ON ITS OWN KIND, which is the case a player creates within a
        // minute of being handed a gravel slab: build a shelf, break the support, watch it come
        // down onto the slab below.
        //
        // IT POPS AS AN ITEM, and that is vanilla's behaviour rather than a defect. The entity
        // resting on a half-height slab has its feet at y+0.5, so blockPosition() floors to the
        // slab's OWN position; FallingBlockEntity then asks that block whether it may be replaced,
        // passing a DirectionalPlaceContext holding ItemStack.EMPTY. SlabBlock only allows the
        // merge when the held item matches, and nothing is held, so it declines and the entity
        // takes the drop-as-item branch - exactly what a falling gravel block does when it lands
        // somewhere it cannot place.
        //
        // A MERGE INTO A DOUBLE WOULD BE NICER AND IS NOT AVAILABLE. Reaching it means overriding
        // canBeReplaced to accept an empty stack, and the empty stack carries no information about
        // what is falling - so the same override would let an anvil or a pointed dripstone delete
        // the slab instead of landing on it. Losing the block to a falling anvil is a worse bug
        // than making the player pick one item up.
        //
        // What this pins is the only outcome that would be a real defect: the slab silently
        // vanishing. Nothing else would notice that - no error, no log line, no drop.
        FTGameTests.test("a_falling_slab_landing_on_its_own_kind_is_not_destroyed", 100, helper -> {
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(SLAB, slab("gravel").defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.setBlock(ABOVE, Blocks.AIR);
            helper.setBlock(HIGH, slab("gravel"));
            helper.succeedWhen(() -> {
                helper.assertBlockPresent(Blocks.AIR, HIGH);
                helper.assertBlockPresent(slab("gravel"), SLAB);

                boolean recovered = helper.getBlockState(ABOVE).is(slab("gravel"))
                    || helper.getEntities(EntityType.ITEM).stream()
                        .anyMatch(item -> item instanceof ItemEntity dropped
                            && dropped.getItem().is(slab("gravel").asItem()));
                if (!recovered) {
                    helper.fail("the falling gravel slab is gone: it neither came to rest nor"
                        + " dropped as an item, so the player simply lost it");
                }
            });
        });

        // A WATERLOGGED SLAB LANDS DRY, and this pairing exists nowhere in vanilla so it is worth
        // pinning rather than trusting. Slabs are waterloggable and no vanilla falling block is, so
        // "a waterlogged block falls" is a state only this mod can reach. If the water came along
        // for the ride, the slab would land full of water with none around it - a block that looks
        // like a bug and cannot be drained.
        //
        // It lands dry because FallingBlockEntity only ever sets WATERLOGGED to true, when the
        // landing position already holds water, and never carries a true value down. Correct, and
        // inherited rather than written here, which is exactly why a change could lose it silently.
        FTGameTests.test("a_waterlogged_slab_does_not_carry_water_down_with_it", 100, helper -> {
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(SLAB, Blocks.AIR);
            helper.setBlock(ABOVE, Blocks.AIR);
            helper.setBlock(HIGH, slab("gravel").defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
            helper.succeedWhen(() -> {
                helper.assertBlockPresent(slab("gravel"), SLAB);
                if (helper.getBlockState(SLAB).getValue(BlockStateProperties.WATERLOGGED)) {
                    helper.fail("the slab landed waterlogged with no water near it, which leaves"
                        + " water sitting inside a block in open air");
                }
            });
        });

        // A DOUBLE IS A WHOLE BLOCK and must land as one. The normalisation in tick() only rewrites
        // TOP, and a switch that caught DOUBLE too would quietly turn a full block of gravel into
        // half of one every time a support was broken - a silent halving of the player's material
        // that no error would report.
        FTGameTests.test("a_falling_double_slab_stays_a_double", 80, helper -> {
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(SLAB, Blocks.AIR);
            helper.setBlock(ABOVE, Blocks.AIR);
            helper.setBlock(HIGH, slab("gravel").defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            helper.succeedWhen(() -> {
                helper.assertBlockPresent(slab("gravel"), SLAB);
                BlockState landed = helper.getBlockState(SLAB);
                if (landed.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    helper.fail("a double gravel slab landed as " + landed.getValue(SlabBlock.TYPE)
                        + ", which is half the block the player had");
                }
            });
        });

        // A BOTTOM SLAB CAN NEVER BE SNOWY, and reading the block above regardless is the obvious
        // bug. A bottom slab's top face is half way up its own position, so a snow layer in the
        // block above floats eight pixels clear of it - painting a snowy side under snow the slab
        // is not touching.
        FTGameTests.test("a_bottom_podzol_slab_is_never_snowy", 20, helper -> {
            helper.setBlock(SLAB, slab("podzol"));
            helper.setBlock(ABOVE, Blocks.SNOW_BLOCK);
            if (helper.getBlockState(SLAB).getValue(
                    SnowyTerrainSlabBlock.SNOWY)) {
                helper.fail("a bottom podzol slab went snowy under snow it does not touch");
            }
            helper.succeed();
        });

        // THE SNOWY OVERRIDE MUST NOT SWALLOW THE SLAB'S OWN UPDATE. Vanilla's SnowyBlock returns
        // early for the UP direction without calling super, which is free for it because its
        // superclass is Block. Copying that shape here skips SlabBlock.updateShape, which schedules
        // the water tick for a waterlogged slab - so a waterlogged podzol slab stops ticking its own
        // fluid whenever the block above it changes, and nothing reports it.
        //
        // THE FIRST VERSION OF THIS TEST ASSERTED THE WRONG THING and would have passed against the
        // bug: it checked that WATERLOGGED survived the update, and `state.setValue(SNOWY, ...)`
        // preserves every other property, so the early return kept the water too. What the early
        // return loses is the SCHEDULED TICK, and that is what has to be asserted.
        FTGameTests.test("a_waterlogged_podzol_slab_still_schedules_its_water_tick", 20, helper -> {
            helper.setBlock(SLAB, slab("podzol").defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP)
                .setValue(BlockStateProperties.WATERLOGGED, true));
            helper.setBlock(ABOVE, Blocks.SNOW_BLOCK);

            BlockPos abs = helper.absolutePos(SLAB);
            if (!helper.getLevel().getFluidTicks().hasScheduledTick(abs, Fluids.WATER)) {
                helper.fail("changing the block above a waterlogged podzol slab left no scheduled"
                    + " water tick, so its fluid has stopped keeping up with the world");
            }
            if (!helper.getBlockState(SLAB).getValue(SnowyTerrainSlabBlock.SNOWY)) {
                helper.fail("and it should still have gone snowy");
            }
            helper.succeed();
        });

        FTGameTests.test("a_top_podzol_slab_goes_snowy_under_snow", 20, helper -> {
            helper.setBlock(SLAB, slab("podzol").defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP));
            helper.setBlock(ABOVE, Blocks.SNOW_BLOCK);
            if (!helper.getBlockState(SLAB).getValue(
                    SnowyTerrainSlabBlock.SNOWY)) {
                helper.fail("a top podzol slab should be snowy: snow rests directly on it");
            }
            helper.succeed();
        });

        FTGameTests.test("bonemeal_grows_roots_under_a_bottom_rooted_dirt_slab", 20, helper -> {
            helper.setBlock(ABOVE, slab("rooted_dirt"));
            helper.setBlock(SLAB, Blocks.AIR);
            boolean applied = BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL),
                helper.getLevel(), helper.absolutePos(ABOVE), null);
            if (!applied) {
                helper.fail("bonemeal did nothing to a bottom rooted dirt slab");
            }
            helper.assertBlockPresent(Blocks.HANGING_ROOTS, SLAB);
            helper.succeed();
        });

        // REFUSING IS THE HONEST ANSWER, not a limitation. Hanging roots need the face above them
        // to be sturdy at the block boundary, and a top slab's underside is half way up its own
        // position - so the roots would start eight pixels low AND immediately pop off. Consuming
        // the bonemeal to grow something that cannot survive is worse than declining.
        FTGameTests.test("a_top_rooted_dirt_slab_refuses_bonemeal", 20, helper -> {
            helper.setBlock(ABOVE, slab("rooted_dirt").defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP));
            helper.setBlock(SLAB, Blocks.AIR);
            boolean applied = BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL),
                helper.getLevel(), helper.absolutePos(ABOVE), null);
            if (applied) {
                helper.fail("a top rooted dirt slab took the bonemeal, and the roots have nothing"
                    + " to hang from");
            }
            helper.assertBlockPresent(Blocks.AIR, SLAB);
            helper.succeed();
        });

        // Vanilla's MudBlock returns a FULL BLOCK for its support and visual shapes, which is true
        // of a mud block and false of a mud slab. Copying that literally would tell the game a half
        // block is a whole one. Only the COLLISION shape is short.
        FTGameTests.test("a_mud_slab_is_soft_to_stand_on_but_still_half_a_block", 20, helper -> {
            helper.setBlock(SLAB, slab("mud"));
            BlockState state = helper.getBlockState(SLAB);
            BlockPos abs = helper.absolutePos(SLAB);

            double collisionTop = state.getCollisionShape(helper.getLevel(), abs).max(
                net.minecraft.core.Direction.Axis.Y);
            double outlineTop = state.getShape(helper.getLevel(), abs, CollisionContext.empty())
                .max(net.minecraft.core.Direction.Axis.Y);

            if (collisionTop >= outlineTop) {
                helper.fail("you should sink into a mud slab: collision top " + collisionTop
                    + " is not below the outline top " + outlineTop);
            }
            if (outlineTop != 0.5D) {
                helper.fail("a mud slab should still be half a block tall, was " + outlineTop);
            }
            helper.succeed();
        });
    }
}
