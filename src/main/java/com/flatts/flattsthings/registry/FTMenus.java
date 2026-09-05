package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.menu.ToolSlotsMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu types. A MenuType exists to bind a screen to a menu; it carries no behaviour itself. */
public final class FTMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, FlattsThings.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ToolSlotsMenu>> TOOL_SLOTS =
        MENUS.register("tool_slots", () -> IMenuTypeExtension.create(
            // Three arguments, not two: the factory is IContainerFactory, which carries a
            // RegistryFriendlyByteBuf of extra data from the server. Nothing is sent here, because
            // the client rebuilds its own container from the player it already has, so the buffer
            // is accepted and ignored rather than not being there at all.
            (containerId, inventory, extraData) -> new ToolSlotsMenu(containerId, inventory)));

    private FTMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
