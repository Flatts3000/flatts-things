package com.flatts.flattsthings;

import com.flatts.flattsthings.gametest.FTGameTests;
import com.flatts.flattsthings.registry.FTAttachments;
import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTCreativeTabs;
import com.flatts.flattsthings.registry.FTItems;
import com.flatts.flattsthings.registry.FTMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point.
 *
 * <p>A grab bag: blocks, tools and small features vanilla never shipped. There is deliberately no
 * spine - nothing here gates anything else, and every addition has to justify itself alone, because
 * a mod like this is only as good as its worst entry.
 */
@Mod(FlattsThings.MOD_ID)
public final class FlattsThings {

    public static final String MOD_ID = "flattsthings";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public FlattsThings(IEventBus modEventBus, ModContainer modContainer) {
        // Blocks before Items (block-items reference their block); creative tab after items.
        FTBlocks.register(modEventBus);
        FTItems.register(modEventBus);
        FTCreativeTabs.register(modEventBus);

        // Per-player storage. Independent of the block registries above, so its position here is
        // convention rather than a constraint.
        FTAttachments.register(modEventBus);
        FTMenus.register(modEventBus);

        // In-world GameTests (the CI gameTest job runs these).
        FTGameTests.register(modEventBus);
    }
}
