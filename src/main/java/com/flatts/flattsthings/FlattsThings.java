package com.flatts.flattsthings;

import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.gametest.FTGameTests;
import com.flatts.flattsthings.registry.FTAttachments;
import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTConditions;
import com.flatts.flattsthings.registry.FTCreativeTabs;
import com.flatts.flattsthings.registry.FTItems;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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

    /**
     * This mod's key-binding category.
     *
     * <p>Here rather than in the client package so a unit test can check the lang file against this
     * exact value. The screen's label is {@code id.toLanguageKey("key.category")}, so the identifier
     * and the translation key are the same fact stated twice, and the failure when they disagree is
     * a raw {@code key.category.flattsthings.flattsthings} rendered in the Key Binds screen - which
     * nothing else here would notice, because no headless test looks at a screen.
     */
    public static final Identifier KEY_CATEGORY =
        Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID);

    public FlattsThings(IEventBus modEventBus, ModContainer modContainer) {
        // FIRST, because the creative tab below reads it while building its contents and a recipe
        // condition reads it during data pack load. Registering a spec does not load the file; it
        // tells FML to, early enough that nothing here has run yet.
        modContainer.registerConfig(ModConfig.Type.COMMON, FTConfig.SPEC);
        FTConditions.register(modEventBus);

        // Blocks before Items (block-items reference their block); creative tab after items.
        FTBlocks.register(modEventBus);
        FTItems.register(modEventBus);
        FTCreativeTabs.register(modEventBus);

        // Per-player storage. Independent of the block registries above, so its position here is
        // convention rather than a constraint. The tool slots themselves are added to vanilla's own
        // InventoryMenu by a mixin, so there is no menu type of ours to register.
        FTAttachments.register(modEventBus);

        // In-world GameTests (the CI gameTest job runs these).
        FTGameTests.register(modEventBus);
    }
}
