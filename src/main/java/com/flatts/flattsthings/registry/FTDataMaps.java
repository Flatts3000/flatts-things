package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.cauldron.CauldronTransform;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Data maps this mod defines. One so far: what an item turns into in a water cauldron.
 *
 * <p>The map lives at {@code data/<namespace>/data_maps/item/cauldron_transform.json} and any pack
 * can add to it, which is what makes this feature a mechanism rather than two special cases.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class FTDataMaps {

    public static final DataMapType<Item, CauldronTransform> CAULDRON_TRANSFORM =
        DataMapType.builder(
            Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, "cauldron_transform"),
            Registries.ITEM,
            CauldronTransform.CODEC).build();

    private FTDataMaps() {
    }

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(CAULDRON_TRANSFORM);
    }
}
