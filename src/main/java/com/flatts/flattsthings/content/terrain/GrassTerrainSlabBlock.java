package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * A grass slab, which is a spreading slab that also takes bonemeal.
 *
 * <p>Mycelium is the other spreading family and is deliberately NOT this class: vanilla's
 * {@code MyceliumBlock} does not implement {@code BonemealableBlock} either, so bonemeal does
 * nothing to it. Checked rather than assumed, because "both of them spread" makes it easy to assume
 * both of them do everything.
 *
 * <p><b>Bonemeal is refused on a BOTTOM slab, the same call {@code RootedDirtSlabBlock} makes for
 * the same reason.</b> Vanilla's walk starts at {@code pos.above()} and places grass and flowers
 * there. For a top or double slab that is the surface. For a bottom slab it is a full block higher
 * than the surface, so every plant would sprout floating half a block over the grass that grew it.
 * Taking the bonemeal and producing that would be worse than declining.
 */
public class GrassTerrainSlabBlock extends SpreadingTerrainSlabBlock implements BonemealableBlock {

    public static final MapCodec<GrassTerrainSlabBlock> CODEC =
        simpleCodec(GrassTerrainSlabBlock::new);

    public GrassTerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(TYPE) != SlabType.BOTTOM
            && level.getBlockState(pos.above()).isAir()
            && level.isInsideBuildHeight(pos.above());
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos,
                                     BlockState state) {
        return true;
    }

    /**
     * Vanilla's own scatter, adapted only in what it recognises as ground.
     *
     * <p>The one changed line is the neighbourhood walk's {@code .is(this)} check, which in vanilla
     * asks whether the block below a candidate is a grass BLOCK. Here {@code this} is the grass
     * slab, so the scatter spreads across a field of grass slabs rather than refusing to leave the
     * first one. Everything else - the 128 attempts, the widening offsets, the one-in-ten chance to
     * bonemeal short grass into tall, the one-in-eight biome feature - is vanilla's, because the
     * point is that bonemealing a grass slab feels like bonemealing grass.
     */
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos,
                                BlockState state) {
        BlockPos above = pos.above();
        BlockState shortGrass = Blocks.SHORT_GRASS.defaultBlockState();
        Optional<Holder.Reference<PlacedFeature>> grassFeature = level.registryAccess()
            .lookupOrThrow(Registries.PLACED_FEATURE)
            .get(VegetationPlacements.GRASS_BONEMEAL);

        outer:
        for (int attempt = 0; attempt < 128; attempt++) {
            BlockPos testPos = above;

            for (int step = 0; step < attempt / 16; step++) {
                testPos = testPos.offset(random.nextInt(3) - 1,
                    (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                if (!level.getBlockState(testPos.below()).is(this)
                        || level.getBlockState(testPos).isCollisionShapeFullBlock(level, testPos)) {
                    continue outer;
                }
            }

            BlockState testState = level.getBlockState(testPos);
            if (testState.is(shortGrass.getBlock()) && random.nextInt(10) == 0) {
                BonemealableBlock bonemealable = (BonemealableBlock) shortGrass.getBlock();
                if (bonemealable.isValidBonemealTarget(level, testPos, testState)) {
                    bonemealable.performBonemeal(level, random, testPos, testState);
                }
            }

            if (testState.isAir() && !level.isOutsideBuildHeight(testPos)) {
                if (random.nextInt(8) == 0) {
                    List<ConfiguredFeature<?, ?>> features = level.getBiome(testPos).value()
                        .getGenerationSettings().getBoneMealFeatures();
                    if (!features.isEmpty()) {
                        Util.getRandom(features, random)
                            .place(level, level.getChunkSource().getGenerator(), random, testPos);
                    }
                } else if (grassFeature.isPresent()) {
                    grassFeature.get().value()
                        .place(level, level.getChunkSource().getGenerator(), random, testPos);
                }
            }
        }
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
