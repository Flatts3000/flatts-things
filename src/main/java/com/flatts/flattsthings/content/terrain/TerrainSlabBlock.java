package com.flatts.flattsthings.content.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

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

    public static final MapCodec<TerrainSlabBlock> CODEC = simpleCodec(TerrainSlabBlock::new);

    public TerrainSlabBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends SlabBlock> codec() {
        return CODEC;
    }
}
