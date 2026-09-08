package com.flatts.flattsthings.client;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.registry.FTBlocks;
import java.util.List;
import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * The grass slab's biome colour.
 *
 * <p><b>A {@code tintindex} in a model does nothing on its own.</b> It names a tint slot; something
 * has to fill it. Vanilla fills the grass block's from {@code BlockTintSources.grassBlock()}, and a
 * modded block gets nothing by default - so the grass slab shipped rendering in the raw greyscale
 * that {@code block/grass_block_top} is drawn in, a flat white-grey, while the vanilla grass block
 * beside it was green.
 *
 * <p><b>No test in this repo could have caught that, and none ever will.</b> Tint is applied during
 * chunk baking on the client; the server has no opinion about it, so every GameTest passes either
 * way and {@code client/**} is excluded from the coverage gate for exactly this reason. It was found
 * by putting a slab next to its block in a dev client and looking at the two of them. That is what
 * devbridge is for and this is the clearest example the repo has.
 *
 * <p>Only the grass block is registered because only the grass block is tinted. Mycelium and podzol
 * paint their colour into the texture, the way vanilla does.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID, value = Dist.CLIENT)
public final class TerrainSlabColors {

    private TerrainSlabColors() {
    }

    @SubscribeEvent
    public static void onRegisterBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        // The same source vanilla uses, so the slab takes the biome's grass colour in the world and
        // the neutral default in a hand - and keeps matching if Mojang retunes either.
        event.register(List.of(BlockTintSources.grassBlock()),
            FTBlocks.terrainSlab("grass_block").get());
    }
}
