package com.flatts.flattsthings.content.cauldron;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What one item becomes when it is dipped in a water cauldron, and what that costs the cauldron.
 *
 * <p><b>A data map rather than a recipe type, and that was a decision.</b> #36 assumed this would
 * define the format {@code #34} (brewing in a cauldron) would reuse. It does not, and pretending
 * otherwise would have meant building a recipe type on speculation. Brewing needs several
 * ingredients, a potion result and a heat source; this needs one item in and one item out, keyed on
 * the item. A data map is exactly that shape, is read straight off the item's registry holder with
 * no lookup, and is half the code. When #34 is built it should define its own format and this should
 * not be bent to fit it.
 *
 * <p><b>An item id and a count rather than an {@code ItemStack}.</b> {@code ItemStack.CODEC} cannot
 * be used here: data maps are read before item components are populated, and it fails with "Item
 * minecraft:white_concrete does not have components yet" - which surfaces as the whole map failing to
 * load and the feature silently doing nothing. The stack is built at conversion time instead, when
 * the registry is fully up.
 *
 * @param result what comes out
 * @param count how many of it, per item converted
 * @param waterCost how many of the cauldron's three levels one conversion drinks
 */
public record CauldronTransform(Item result, int count, int waterCost) {

    public static final Codec<CauldronTransform> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result")
                .forGetter(CauldronTransform::result),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(CauldronTransform::count),
            Codec.intRange(0, 3).optionalFieldOf("water_cost", 1)
                .forGetter(CauldronTransform::waterCost)
        ).apply(instance, CauldronTransform::new));

    /** One conversion's worth, for {@code converted} input items. */
    public ItemStack resultFor(int converted) {
        return new ItemStack(this.result, this.count * converted);
    }
}
