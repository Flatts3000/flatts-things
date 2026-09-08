package com.flatts.flattsthings.content.terrain;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The one place that turns a slab of one material into a slab of another.
 *
 * <p>Grass dying back to dirt, dirt greening into grass, podzol dropping a dirt slab - each of those
 * has to carry the SHAPE across while changing the material. Writing that conversion out at each
 * call site is how a top slab quietly becomes a bottom one, or a waterlogged slab comes out dry,
 * somewhere in the middle of a spread that nobody is watching.
 */
final class TerrainSlabs {

    private TerrainSlabs() {
    }

    /**
     * A slab of {@code block} with the same half and the same water as {@code like}.
     *
     * <p><b>SNOWY is deliberately NOT copied and is left at its default.</b> It is a function of
     * what is in the block above rather than of the slab itself, and every caller here goes through
     * {@code setBlockAndUpdate}, whose neighbour update runs {@code updateShape} and computes it
     * correctly. Copying it across would be copying an answer to a question the new block has not
     * been asked yet - and the two families do not even agree on the question, since only some of
     * them carry the property at all.
     */
    static BlockState sameShape(Block block, BlockState like) {
        return block.defaultBlockState()
            .setValue(SlabBlock.TYPE, like.getValue(SlabBlock.TYPE))
            .setValue(BlockStateProperties.WATERLOGGED,
                like.getValue(BlockStateProperties.WATERLOGGED));
    }
}
