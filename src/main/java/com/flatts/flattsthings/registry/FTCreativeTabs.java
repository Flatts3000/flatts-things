package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tab. One tab for the whole mod.
 *
 * <p>The accept order below is the public item ordering - JEI and EMI read it too - so it is kept
 * grouped by what a thing is for, not by the order things were built. An item missing from here is
 * invisible in creative and in the JEI panel while working perfectly in every test, so
 * {@code every_mod_item_is_in_the_creative_tab} in the GameTests holds it honest.
 */
public final class FTCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlattsThings.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLATTS_THINGS_TAB =
        CREATIVE_MODE_TABS.register(
            "flattsthings",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.flattsthings"))
                .icon(() -> FTItems.PLATES.get("stone").get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    // --- Redstone: the player pressure plates, in vanilla's material order ---
                    // Contents are rebuilt when the config changes, so this is not a one-shot read.
                    if (FTConfig.playerPressurePlates()) {
                        FTItems.PLATES.values().forEach(plate -> output.accept(plate.get()));
                    }

                    // --- Building: the woodcutter ---
                    // Gated like everything else. The BLOCK is registered unconditionally, because a
                    // registry that changes with a config file is how two sides end up disagreeing
                    // about what exists; the switch decides whether a player can obtain one.
                    if (FTConfig.woodCutting()) {
                        output.accept(FTItems.WOODCUTTER.get());
                    }
                })
                .build());

    private FTCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
