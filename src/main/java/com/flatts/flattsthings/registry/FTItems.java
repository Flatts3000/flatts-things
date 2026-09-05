package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry. Block-items live here rather than beside their blocks, so load order stays explicit. */
public final class FTItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(FlattsThings.MOD_ID);

    public static final DeferredItem<BlockItem> PLAYER_PRESSURE_PLATE =
        ITEMS.registerSimpleBlockItem("player_pressure_plate", FTBlocks.PLAYER_PRESSURE_PLATE);

    private FTItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
