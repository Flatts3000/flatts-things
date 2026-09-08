package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A slab of mud, which you sink two pixels into exactly as you do a mud block.
 *
 * <p>Vanilla's {@code MudBlock} is four overrides on top of a plain block: a collision shape two
 * pixels shy of the top, a full-block support and visual shape so it still looks and behaves solid,
 * no pathfinding through it, and a darker shade. Three of those carry over unchanged. The one that
 * does not is the pair of shape overrides, and the reason is worth stating because copying them
 * literally is the obvious mistake.
 *
 * <p><b>Vanilla returns a FULL BLOCK for the support and visual shapes. A slab must return the
 * slab's shape instead.</b> Vanilla can be blunt about it because a mud block genuinely fills its
 * position, so "full block" is both the truth and a convenient constant. Restating that here would
 * tell the game a half block is a whole one, which decides where torches can be placed, what a
 * neighbouring block culls its faces against, and whether the space above a bottom slab is
 * treated as solid. The sink is a collision-only illusion in vanilla too; it is only the collision
 * shape that is short.
 */
public class MudSlabBlock extends TerrainSlabBlock {

    public static final MapCodec<MudSlabBlock> CODEC = simpleCodec(MudSlabBlock::new);

    /** Two pixels shy of the top of each half, which is vanilla's own 16-to-14 sink. */
    private static final VoxelShape BOTTOM_SHAPE = Block.column(16.0, 0.0, 6.0);
    private static final VoxelShape TOP_SHAPE = Block.column(16.0, 8.0, 14.0);
    private static final VoxelShape DOUBLE_SHAPE = Block.column(16.0, 0.0, 14.0);

    public MudSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return switch (state.getValue(TYPE)) {
            case BOTTOM -> BOTTOM_SHAPE;
            case TOP -> TOP_SHAPE;
            case DOUBLE -> DOUBLE_SHAPE;
        };
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 0.2F;
    }
}
