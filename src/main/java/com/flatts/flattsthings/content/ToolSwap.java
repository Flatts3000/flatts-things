package com.flatts.flattsthings.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * A swap in progress: which tool slot the tool came out of, which hotbar slot it went into, and what
 * it displaced.
 *
 * <p><b>Persistent, not transient, and that is the whole safety argument.</b> While a swap is live
 * the displaced item exists in exactly one place: here. If this were a map in memory, a crash or a
 * restart mid-swing would destroy whatever the player had been holding, and they would have no idea
 * why. Serialised, the swap can be unwound on next login instead.
 *
 * <p>The same shape as recompile's Scrap Hauler invariant: a thing that exists exactly once, in one
 * of two places, with every path that could break that pinned by a test.
 */
public record ToolSwap(int toolSlot, int hotbarSlot, ItemStack displaced) {

    /** No swap in progress. A negative tool slot is the sentinel. */
    public static final ToolSwap NONE = new ToolSwap(-1, -1, ItemStack.EMPTY);

    public static final Codec<ToolSwap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("tool_slot").forGetter(ToolSwap::toolSlot),
        Codec.INT.fieldOf("hotbar_slot").forGetter(ToolSwap::hotbarSlot),
        ItemStack.OPTIONAL_CODEC.fieldOf("displaced").forGetter(ToolSwap::displaced)
    ).apply(instance, ToolSwap::new));

    public boolean active() {
        return this.toolSlot >= 0;
    }
}
