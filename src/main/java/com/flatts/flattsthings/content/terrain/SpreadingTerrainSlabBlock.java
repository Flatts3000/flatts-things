package com.flatts.flattsthings.content.terrain;

import com.flatts.flattsthings.registry.FTBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * A slab that spreads and dies back: grass and mycelium.
 *
 * <p>Vanilla puts all of this in {@code SpreadingSnowyBlock}, which no slab can extend because
 * {@code SlabBlock} is already the parent. So the logic is adapted rather than inherited, and the
 * conditions below are vanilla's own - the light check, the fluid check and the 3x5x3 neighbourhood
 * are copied so a grass slab lives and dies on the same terms a grass block does.
 *
 * <p><b>Spreading runs in two directions and only one of them is here.</b> This class PUSHES: it
 * converts nearby dirt, whether that dirt is a full block or one of this mod's dirt slabs. What it
 * cannot do is the reverse - vanilla's own {@code GrassBlock} looks for {@code Blocks.DIRT} and will
 * never see a dirt slab, and teaching it to would need a second mixin. That direction is handled by
 * {@link DirtTerrainSlabBlock}, which pulls instead: the dirt slab looks for a vanilla grass or
 * mycelium block and converts itself. Between them every combination is covered with no mixin.
 *
 * <p><b>What "covered" means for a bottom slab is an approximation, deliberately.</b> The light
 * check reads {@code pos.above()}, which for a top or double slab is the block resting directly on
 * it. For a bottom slab there is half a block of air in between, so the check is slightly generous -
 * a bottom grass slab survives under something a full grass block would die under. The alternative
 * is inventing a half-block light rule that vanilla has no equivalent for, and being generous is the
 * failure that costs a player nothing.
 */
public class SpreadingTerrainSlabBlock extends SnowyTerrainSlabBlock {

    public static final MapCodec<SpreadingTerrainSlabBlock> CODEC =
        simpleCodec(SpreadingTerrainSlabBlock::new);

    public SpreadingTerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    /**
     * Whether the surface is uncovered enough to keep growing.
     *
     * <p><b>Measured with a full block of DIRT standing in for the slab, and that substitution is
     * the whole trick.</b> Vanilla asks {@code LightEngine.getLightBlockInto(state, aboveState, UP,
     * ...)} - how much light is blocked entering THIS block from above. For a full grass block that
     * is the same question as "is my surface covered", because the surface and the block boundary
     * are the same plane.
     *
     * <p>For a slab they are different questions, and asking vanilla's gives the wrong answer in the
     * worst possible way. {@code SlabBlock.useShapeForLightOcclusion()} returns true, so the engine
     * computes real shape occlusion rather than taking the opaque-block shortcut - and a TOP slab's
     * own material seals its own top face. Passed its own state, the check reports 15 with nothing
     * above it at all, so **every top grass slab would have died back to dirt on its first random
     * tick, under open sky**. Found by driving the matching-halves test red and watching it refuse
     * to fail.
     *
     * <p>Dirt is the stand-in because it is opaque, takes the shortcut path, and is what both
     * families revert to anyway. What is being measured is what the block ABOVE does, which is what
     * vanilla measures too.
     *
     * <p><b>It therefore takes no state, and vanilla's version does.</b> Dropping the parameter is
     * deliberate rather than tidying: a signature that still accepted one would say the answer
     * depends on which slab is asking, and the whole point of the fix is that it must not.
     */
    static boolean canStayAlive(LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.is(Blocks.SNOW) && aboveState.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        }
        if (aboveState.getFluidState().isFull()) {
            return false;
        }
        BlockState asFullBlock = Blocks.DIRT.defaultBlockState();
        int dampening = LightEngine.getLightBlockInto(asFullBlock, aboveState, Direction.UP,
            aboveState.getLightDampening());
        return dampening < 15;
    }

    static boolean canPropagate(LevelReader level, BlockPos pos) {
        return canStayAlive(level, pos)
            && !level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    /**
     * Die back when covered, and spread when not.
     *
     * <p>Order matters and is vanilla's: a block that cannot stay alive reverts and does NOT also
     * get to spread in the same tick.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random) {
        if (!canStayAlive(level, pos)) {
            if (!level.isAreaLoaded(pos, 1)) {
                return;
            }
            level.setBlockAndUpdate(pos, TerrainSlabs.sameShape(
                FTBlocks.terrainSlab("dirt").get(), state));
            return;
        }
        if (!level.isAreaLoaded(pos, 3) || level.getMaxLocalRawBrightness(pos.above()) < 9) {
            return;
        }

        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos testPos = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3,
                random.nextInt(3) - 1);
            BlockState target = level.getBlockState(testPos);

            if (target.is(Blocks.DIRT)) {
                // A full block of dirt becomes a full block of this family, not a slab. The slab is
                // the SOURCE here; what it is spreading onto keeps its own shape.
                if (canPropagate(level, testPos)) {
                    level.setBlockAndUpdate(testPos, withSnowy(fullBlock().defaultBlockState(),
                        level, testPos));
                }
            } else if (target.is(FTBlocks.terrainSlab("dirt").get())
                    && target.getValue(TYPE) == state.getValue(TYPE)) {
                // MATCHING HALVES ONLY. A bottom grass slab has no business turning a top dirt slab
                // green: they do not touch, and the result would be grass growing on a surface
                // nothing is resting on.
                if (canPropagate(level, testPos)) {
                    level.setBlockAndUpdate(testPos,
                        withSnowy(TerrainSlabs.sameShape(this, target), level, testPos));
                }
            }
        }
    }

    /**
     * Set SNOWY from what is above, because nothing else will.
     *
     * <p>Vanilla does this explicitly when it spreads, and the first version here left it to the
     * default on the stated grounds that {@code setBlockAndUpdate} would recompute it. **That
     * reasoning was wrong.** {@code updateNeighbourShapes} runs {@code updateShape} on the
     * NEIGHBOURS of the block just written, not on the block itself, so nothing ever asked the new
     * grass slab what was above it.
     *
     * <p>Visible where {@code canStayAlive} explicitly allows it: a slab under a single layer of
     * snow, greened by a neighbour, drew the plain side texture instead of the snowy one until some
     * unrelated update happened to touch the block above it.
     */
    private static BlockState withSnowy(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.hasProperty(SNOWY)) {
            return state;
        }
        return state.setValue(SNOWY, level.getBlockState(pos.above()).is(BlockTags.SNOW));
    }

    /** The full vanilla block this slab is half of, used when spreading onto a full block of dirt. */
    private Block fullBlock() {
        return this == FTBlocks.terrainSlab("mycelium").get() ? Blocks.MYCELIUM : Blocks.GRASS_BLOCK;
    }

}
