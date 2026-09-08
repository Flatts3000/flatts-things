package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A slab that falls: gravel, sand and red sand.
 *
 * <p><b>The falling logic is copied from {@code FallingBlock} rather than inherited, and that is
 * forced rather than chosen.</b> {@code FallingBlock} extends {@code Block} and so does
 * {@code SlabBlock}; Java has one superclass, so a falling slab cannot be both. Copying the smaller
 * of the two is the only option, and falling is much the smaller: it is a scheduled tick, a check
 * that the space below is free, and one call to {@code FallingBlockEntity.fall}. Reimplementing the
 * slab half would have meant the type property, the shape, waterlogging and the doubling rule.
 *
 * <p>Kept deliberately identical to {@code FallingBlock}'s version, including the two-tick delay,
 * so that a gravel slab falls exactly when a gravel block would. If vanilla changes how falling
 * works, this is the file that has to be brought back into step, and that cost is the price of the
 * inheritance clash rather than a mistake.
 */
public class FallingTerrainSlabBlock extends TerrainSlabBlock implements Fallable {

    public static final MapCodec<FallingTerrainSlabBlock> CODEC =
        simpleCodec(FallingTerrainSlabBlock::new);

    public FallingTerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        level.scheduleTick(pos, this, DELAY_AFTER_PLACE);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction directionToNeighbour,
                                     BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        ticks.scheduleTick(pos, this, DELAY_AFTER_PLACE);
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos,
            neighbourState, random);
    }

    /**
     * Falls, and lands the right way up.
     *
     * <p><b>A TOP slab falls as a BOTTOM one</b>, which is the one place this deliberately differs
     * from a straight copy. {@code FallingBlockEntity} carries the state it was given and places it
     * unchanged on landing, so a top slab knocked loose would come to rest as a top slab: a
     * half-block of gravel floating with a gap underneath it, resting on nothing. Nothing else in
     * the game produces that, and a player would read it as a bug rather than as a rule.
     *
     * <p>A DOUBLE slab is left alone, because a double IS a full block and lands as one correctly.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!FallingBlock.isFree(level.getBlockState(pos.below())) || pos.getY() < level.getMinY()) {
            return;
        }
        BlockState falling = state.getValue(TYPE) == SlabType.TOP
            ? state.setValue(TYPE, SlabType.BOTTOM)
            : state;
        FallingBlockEntity.fall(level, pos, falling);
    }

    /**
     * A double that could not be placed is worth TWO slabs, and vanilla drops one.
     *
     * <p>{@code FallingBlockEntity} calls this immediately before {@code spawnAtLocation(level,
     * block)}, which spawns a single item of the block whatever state it was carrying. For every
     * vanilla falling block that is correct, because a block is a block. A DOUBLE slab is two, so
     * the player would silently lose half the material - no message, no log line, and the amount is
     * small enough that nobody would be sure it had happened.
     *
     * <p>Reachable the same way the single-slab case is: a double gravel slab coming to rest on an
     * existing slab, where {@code SlabBlock.canBeReplaced} refuses the empty stack that
     * {@code DirectionalPlaceContext} carries and the entity takes the drop-as-item branch.
     */
    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
        if (entity.getBlockState().getValue(TYPE) == SlabType.DOUBLE) {
            Block.popResource(level, pos, new ItemStack(this));
        }
    }

    /**
     * A double slab must never come to rest waterlogged, and only a fall can put it there.
     *
     * <p>{@code SlabBlock} refuses the combination deliberately: both {@code placeLiquid} and
     * {@code canPlaceLiquid} return false for {@code DOUBLE}, because a double slab fills its
     * position and there is nowhere for the water to be. {@code FallingBlockEntity} does not ask
     * either of them - it sets {@code WATERLOGGED} directly whenever the landing position holds
     * water - so dropping a double gravel slab into a one-deep pool produces a solid full block
     * that is also a water source, a state the game will not otherwise create.
     */
    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaced,
                       FallingBlockEntity entity) {
        if (state.getValue(TYPE) == SlabType.DOUBLE
                && state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), 3);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(16) == 0 && FallingBlock.isFree(level.getBlockState(pos.below()))) {
            ParticleUtils.spawnParticleBelow(level, pos, random,
                new BlockParticleOption(ParticleTypes.FALLING_DUST, state));
        }
    }

    /** Vanilla's own delay, restated because {@code getDelayAfterPlace} lives on the class we cannot extend. */
    private static final int DELAY_AFTER_PLACE = 2;
}
