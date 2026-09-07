package com.flatts.flattsthings.content.woodcutter;

import com.flatts.flattsthings.registry.FTBlocks;
import com.flatts.flattsthings.registry.FTRecipes;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * One cut: some planks in, a shape out.
 *
 * <p><b>A recipe type of this mod's own, and the first version did not have one.</b> It wrote
 * {@code minecraft:stonecutting} recipes so the VANILLA stonecutter would cut wood, which works -
 * {@code StonecutterMenu} has no ingredient restriction at all - and was rejected (owner,
 * 2026-09-07): a stone saw is the wrong block to be cutting planks on. The recipes are the same
 * shape; only the type and the block that reads them changed.
 *
 * <p><b>Extends {@code SingleItemRecipe}, which is what {@code StonecutterRecipe} is.</b> One
 * ingredient, one result, no grid - the base class carries the codecs, the matching and the assembly,
 * so this is a type tag and a display.
 */
public class WoodCuttingRecipe extends SingleItemRecipe {

    public static final MapCodec<WoodCuttingRecipe> MAP_CODEC =
        simpleMapCodec(WoodCuttingRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, WoodCuttingRecipe> STREAM_CODEC =
        simpleStreamCodec(WoodCuttingRecipe::new);

    public WoodCuttingRecipe(Recipe.CommonInfo commonInfo, Ingredient ingredient,
                             ItemStackTemplate result) {
        super(commonInfo, ingredient, result);
    }

    @Override
    public RecipeType<WoodCuttingRecipe> getType() {
        return FTRecipes.WOOD_CUTTING_TYPE.get();
    }

    @Override
    public RecipeSerializer<WoodCuttingRecipe> getSerializer() {
        return FTRecipes.WOOD_CUTTING_SERIALIZER.get();
    }

    @Override
    public String group() {
        return "";
    }

    /**
     * <b>Borrows the stonecutter's display shape, deliberately.</b> A {@code RecipeDisplay} is how a
     * recipe describes itself to the recipe book, and 26.1 has no generic "one in, one out" display
     * to reach for - {@code StonecutterRecipeDisplay} is that shape with a workstation attached, so
     * it is used with the WOODCUTTER named as the station. The alternative is a display type of our
     * own for no visible difference.
     */
    @Override
    public List<RecipeDisplay> display() {
        return List.of(new StonecutterRecipeDisplay(
            this.input().display(),
            new SlotDisplay.ItemStackSlotDisplay(this.result()),
            new SlotDisplay.ItemSlotDisplay(FTBlocks.WOODCUTTER.get().asItem())));
    }

    /**
     * <b>The stonecutter's book category, because a new one is not registrable.</b>
     * {@code RecipeBookCategories} is a fixed list of static fields rather than a registry, so this
     * is the closest true thing: a cutting recipe. It decides nothing about behaviour, only which
     * heading the recipe book files it under.
     */
    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.STONECUTTER;
    }
}
