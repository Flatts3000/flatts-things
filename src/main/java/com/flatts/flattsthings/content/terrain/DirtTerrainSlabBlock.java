package com.flatts.flattsthings.content.terrain;

import com.flatts.flattsthings.registry.FTBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A dirt slab, which greens itself when there is grass or mycelium next to it.
 *
 * <p><b>This is the half of spreading that has to run backwards, and the reason is a hard limit.</b>
 * Vanilla's {@code GrassBlock} spreads by looking for {@code Blocks.DIRT} in a 3x5x3 neighbourhood.
 * It will never see a dirt slab, and there is no event or data hook that changes what it looks for -
 * teaching it would mean a mixin on {@code GrassBlock}, and this repo has exactly one mixin with a
 * written reason. Without something here, a dirt slab laid beside a grass block would stay bare
 * dirt forever, which is the kind of thing a player files a bug about.
 *
 * <p>So the dirt slab PULLS: it looks for a vanilla grass or mycelium block near it and converts
 * itself. {@link SpreadingTerrainSlabBlock} pushes in the other direction, covering slab-to-slab and
 * slab-to-full-block. Between them every combination works and no mixin is needed.
 *
 * <p><b>Only vanilla blocks are pulled from, deliberately.</b> The push already handles a grass SLAB
 * converting a dirt slab, so looking for those here as well would mean two mechanisms racing to do
 * the same job - twice the chances per tick, and a spread rate that quietly differs from vanilla's
 * for no stated reason.
 *
 * <p><b>Ticking at all is a divergence from vanilla dirt</b>, which does not random-tick. It is the
 * price of the limit above: a block that has to notice its neighbours has to be given a moment to
 * look.
 */
public class DirtTerrainSlabBlock extends TerrainSlabBlock {

    public static final MapCodec<DirtTerrainSlabBlock> CODEC =
        simpleCodec(DirtTerrainSlabBlock::new);

    public DirtTerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random) {
        if (!level.isAreaLoaded(pos, 3)) {
            return;
        }
        // The same gate the source side uses, asked here instead: grass needs light to spread INTO
        // a place, and this is the place.
        if (level.getMaxLocalRawBrightness(pos.above()) < 9
                || !SpreadingTerrainSlabBlock.canPropagate(state, level, pos)) {
            return;
        }

        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos testPos = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3,
                random.nextInt(3) - 1);
            Block became = slabFor(level.getBlockState(testPos));
            if (became != null) {
                level.setBlockAndUpdate(pos, TerrainSlabs.sameShape(became, state));
                return;
            }
        }
    }

    /** The slab this should become for a vanilla source block, or null if that block is not one. */
    private static Block slabFor(BlockState source) {
        if (source.is(Blocks.GRASS_BLOCK)) {
            return FTBlocks.terrainSlab("grass_block").get();
        }
        if (source.is(Blocks.MYCELIUM)) {
            return FTBlocks.terrainSlab("mycelium").get();
        }
        return null;
    }
}
