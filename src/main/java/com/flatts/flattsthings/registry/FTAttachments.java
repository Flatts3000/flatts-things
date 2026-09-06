package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.ToolSlots;
import com.flatts.flattsthings.content.ToolSwap;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge data attachments: per-player state that is not an item and not a block. */
public final class FTAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FlattsThings.MOD_ID);

    /**
     * A player's tool slots.
     *
     * <p><b>{@code copyOnDeath()} is set explicitly and it is load-bearing.</b> NeoForge copies an
     * attachment onto the respawned player only for types that opt in. Without it every tool a
     * player owns is destroyed the first time they fall in lava, and nobody files that as a bug
     * because it reads as the mod eating your gear rather than as a missing flag.
     * {@code tool_slots_survive_death} pins it.
     */
    public static final Supplier<AttachmentType<ToolSlots>> TOOL_SLOTS =
        ATTACHMENTS.register("tool_slots",
            () -> AttachmentType.builder(ToolSlots::new)
                .serialize(ToolSlots.CODEC.fieldOf("tool_slots"))
                .copyOnDeath()
                .build());

    /**
     * The swap in progress, if any.
     *
     * <p>Serialised for safety rather than for saving: it holds the item the player was carrying
     * when the swap began, so losing it on a crash means losing their stack. {@code copyOnDeath} for
     * the same reason - a death mid-swing must not eat the displaced item on top of everything else
     * the player just dropped.
     */
    public static final Supplier<AttachmentType<ToolSwap>> TOOL_SWAP =
        ATTACHMENTS.register("tool_swap",
            () -> AttachmentType.builder(() -> ToolSwap.NONE)
                .serialize(ToolSwap.CODEC.fieldOf("tool_swap"))
                .copyOnDeath()
                .build());

    /**
     * Whether this player wants the auto-swap, which is not the same question as whether the pack
     * allows it.
     *
     * <p>{@code FTConfig.toolAutoSwap()} is the pack author's switch and applies to everyone; this
     * is the player's own, toggled with a key. Both must say yes. Keeping them separate is what
     * stops a player's preference being silently overwritten by a config reload, and stops a player
     * turning on something a pack deliberately turned off.
     *
     * <p>Serialised and {@code copyOnDeath}, because a preference that resets when you die or log
     * out is not a preference. Default on, so the feature works without anybody finding the key.
     */
    public static final Supplier<AttachmentType<Boolean>> AUTO_SWAP_WANTED =
        ATTACHMENTS.register("auto_swap_wanted",
            () -> AttachmentType.builder(() -> Boolean.TRUE)
                .serialize(Codec.BOOL.fieldOf("auto_swap_wanted"))
                .copyOnDeath()
                .build());

    private FTAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
