package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A slab that changes its side texture when snow sits on it. Podzol is the only one shipped.
 *
 * <p>Vanilla's {@code SnowyBlock} is a plain {@code Block}, so this restates its three parts - the
 * property, the neighbour update and the placement state - on top of {@code SlabBlock}. That is the
 * same inheritance clash the falling slab has and the same resolution: copy the smaller half.
 *
 * <p><b>A BOTTOM slab is never snowy, and that is the one real difference from vanilla.</b> Vanilla
 * asks what is in the block above. For a top slab or a double that is the right question, because
 * the slab's upper face IS the block boundary and snow rests directly on it. A bottom slab's upper
 * face sits half way up its own block, with air above it inside the same position, so nothing can
 * ever rest on it - a snow layer placed in the block above would float eight pixels clear. Reading
 * the block above for a bottom slab would therefore paint a snowy side under a snowdrift the slab
 * is not touching.
 */
public class SnowyTerrainSlabBlock extends TerrainSlabBlock {

    public static final MapCodec<SnowyTerrainSlabBlock> CODEC =
        simpleCodec(SnowyTerrainSlabBlock::new);

    public static final BooleanProperty SNOWY = BlockStateProperties.SNOWY;

    public SnowyTerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SNOWY, false));
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SNOWY);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour,
                                     BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        if (directionToNeighbour == Direction.UP) {
            return state.setValue(SNOWY, snowyOn(state, neighbourState));
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
            neighbourState, random);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        if (placed == null) {
            return null;
        }
        BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
        return placed.setValue(SNOWY, snowyOn(placed, above));
    }

    /** Snow above only counts when the slab's top face is the block boundary. See the class note. */
    private static boolean snowyOn(BlockState state, BlockState aboveState) {
        return state.getValue(TYPE) != SlabType.BOTTOM && aboveState.is(BlockTags.SNOW);
    }
}
