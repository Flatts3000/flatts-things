package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.woodcutter.WoodCuttingRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Recipe types and serializers this mod adds. One so far: {@code flattsthings:wood_cutting}.
 *
 * <p><b>The registered name is part of the data format</b>, the same way the feature condition's is:
 * it appears in every generated recipe as {@code "type": "flattsthings:wood_cutting"}, so renaming it
 * silently drops all of them.
 */
public final class FTRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, FlattsThings.MOD_ID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, FlattsThings.MOD_ID);

    public static final Supplier<RecipeType<WoodCuttingRecipe>> WOOD_CUTTING_TYPE =
        RECIPE_TYPES.register("wood_cutting", () -> RecipeType.simple(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                FlattsThings.MOD_ID, "wood_cutting")));

    public static final Supplier<RecipeSerializer<WoodCuttingRecipe>> WOOD_CUTTING_SERIALIZER =
        RECIPE_SERIALIZERS.register("wood_cutting", () -> new RecipeSerializer<>(
            WoodCuttingRecipe.MAP_CODEC, WoodCuttingRecipe.STREAM_CODEC));

    private FTRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
