package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.registry.FTBlocks.PlateVariant;
import com.flatts.flattsthings.registry.FTBlocks.TerrainSlabVariant;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry. Block-items live here rather than beside their blocks, so load order stays explicit. */
public final class FTItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(FlattsThings.MOD_ID);

    /** Block-items for the plates, keyed by material, in {@link FTBlocks#VARIANTS} order. */
    public static final Map<String, DeferredItem<BlockItem>> PLATES = new LinkedHashMap<>();

    static {
        for (PlateVariant variant : FTBlocks.VARIANTS) {
            PLATES.put(variant.material(),
                ITEMS.registerSimpleBlockItem(variant.blockId(), FTBlocks.plate(variant.material())));
        }
    }

    /** Block-items for the terrain slabs, keyed by family, in {@link FTBlocks#TERRAIN_SLABS} order. */
    public static final Map<String, DeferredItem<BlockItem>> TERRAIN = new LinkedHashMap<>();

    static {
        for (TerrainSlabVariant variant : FTBlocks.TERRAIN_SLABS) {
            TERRAIN.put(variant.family(), ITEMS.registerSimpleBlockItem(
                variant.blockId(), FTBlocks.terrainSlab(variant.family())));
        }
    }

    private FTItems() {
    }

    /** The woodcutter's block item. */
    public static final net.neoforged.neoforge.registries.DeferredItem<
        net.minecraft.world.item.BlockItem> WOODCUTTER =
            ITEMS.registerSimpleBlockItem("woodcutter", FTBlocks.WOODCUTTER);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
