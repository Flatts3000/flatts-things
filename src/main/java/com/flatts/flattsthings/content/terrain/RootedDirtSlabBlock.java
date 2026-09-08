package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A slab of rooted dirt, which grows hanging roots underneath when bonemealed.
 *
 * <p>Vanilla's {@code RootedDirtBlock} is three small methods and they carry over almost unchanged.
 * The exception is which slabs are a valid target.
 *
 * <p><b>A TOP slab is NOT bonemealable, and refusing is the honest answer rather than a limitation.
 * </b> Hanging roots occupy the block below and hang from its ceiling, so they need the face above
 * them to be solid at the block boundary. A bottom slab's underside IS that boundary and roots hang
 * from it correctly. A top slab's underside sits half way up its own position, so roots placed
 * below would start a full eight pixels lower with a visible gap - and would not survive anyway,
 * because {@code HangingRootsBlock} asks whether the face above it is sturdy and a top slab's is
 * not. Consuming the bonemeal to grow something that immediately pops off would be worse than
 * declining, so this declines.
 *
 * <p>A DOUBLE slab is a whole block and behaves exactly like rooted dirt, because it is.
 */
public class RootedDirtSlabBlock extends TerrainSlabBlock implements BonemealableBlock {

    public static final MapCodec<RootedDirtSlabBlock> CODEC = simpleCodec(RootedDirtSlabBlock::new);

    public RootedDirtSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(TYPE) != SlabType.TOP
            && level.getBlockState(pos.below()).isAir()
            && level.isInsideBuildHeight(pos.below());
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos,
                                     BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos,
                                BlockState state) {
        level.setBlockAndUpdate(pos.below(), Blocks.HANGING_ROOTS.defaultBlockState());
    }

    @Override
    public BlockPos getParticlePos(BlockPos blockPos) {
        return blockPos.below();
    }
}
