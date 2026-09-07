package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.woodcutter.WoodcutterMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu types this mod adds. One so far: the woodcutter's.
 *
 * <p>The tool slots deliberately have none - they are appended to vanilla's own {@code InventoryMenu}
 * by a mixin, because that menu is rebuilt on join, respawn and dimension change and a hook for each
 * would be one hook too few. A woodcutter is a screen of its own, so it gets a type of its own.
 */
public final class FTMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, FlattsThings.MOD_ID);

    /**
     * <b>{@code IMenuTypeExtension.create}, not {@code new MenuType<>}</b>, which is one of the 26.1
     * renames worth remembering: the factory takes three arguments including a
     * {@code RegistryFriendlyByteBuf}. This menu sends nothing extra at open time - the options are
     * ordinary synced slots - so the buffer is ignored.
     */
    public static final Supplier<MenuType<WoodcutterMenu>> WOODCUTTER =
        MENUS.register("woodcutter", () -> IMenuTypeExtension.create(
            (containerId, inventory, buffer) -> new WoodcutterMenu(containerId, inventory)));

    private FTMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
