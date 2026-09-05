package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.ToolSlots;
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

    private FTAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
