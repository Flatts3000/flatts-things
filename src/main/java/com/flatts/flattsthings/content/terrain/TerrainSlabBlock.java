package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A slab of a terrain block: dirt, mud, clay and the rest.
 *
 * <p><b>{@code SlabBlock} can be subclassed, and that is not obvious here.</b> Two other blocks in
 * this mod could not extend the vanilla class they resemble - {@code PressurePlateBlock} declares
 * {@code codec()} as {@code MapCodec<PressurePlateBlock>} and {@code StonecutterBlock} the same,
 * and generics are invariant, so no subclass can override either. {@code SlabBlock} declares
 * {@code MapCodec<? extends SlabBlock>}, a wildcard, so it is open. Checked against the 26.1 source
 * rather than assumed from the two that were not, because assuming would have meant reimplementing
 * the type property, the shape, the waterlogging and the doubling by hand.
 *
 * <p>Everything a slab does comes with that inheritance. What the subclasses in this package add is
 * the behaviour of the material: gravel falls, podzol carries the snowy state, rooted dirt grows
 * roots. That split is the whole design - the slab half is vanilla's and is not re-litigated, and
 * only the material half is ours.
 */
public class TerrainSlabBlock extends SlabBlock {

    /**
     * Nothing grows on the top of a BOTTOM slab, whatever the tags say.
     *
     * <p>Which FAMILIES can hold a plant is a tag question and is answered in data: the generator
     * adds the seven soil families to {@code #minecraft:supports_vegetation}, mirroring the vanilla
     * blocks they are halves of. Which HALVES can is a blockstate question, and a tag cannot express
     * it - so it is answered here.
     *
     * <p>A bottom slab's upper face is half way up its own position. A plant placed on it goes in
     * the block above and renders from the bottom of that space, so it would sprout floating eight
     * pixels clear of the soil. Vetoing is the honest answer.
     *
     * <p><b>Without any of this, nothing would grow on ANY terrain slab.</b>
     * {@code VegetationBlock.canSurvive} asks this method first and falls back to the tag; a modded
     * block that answers neither supports nothing. Bone meal on a grass slab was accepted and
     * consumed and placed nothing at all, because every plant it tried to put down failed
     * {@code canSurvive} on the way in.
     */
    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos,
                                    Direction facing, BlockState plant) {
        if (state.getValue(TYPE) == SlabType.BOTTOM) {
            return TriState.FALSE;
        }
        return super.canSustainPlant(state, level, pos, facing, plant);
    }

    public static final MapCodec<TerrainSlabBlock> CODEC = simpleCodec(TerrainSlabBlock::new);

    public TerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }
}
