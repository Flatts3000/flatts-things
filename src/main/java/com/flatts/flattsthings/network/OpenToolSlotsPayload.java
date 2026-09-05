package com.flatts.flattsthings.network;

import com.flatts.flattsthings.FlattsThings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * "Open my tool slots", sent when the player presses the key.
 *
 * <p>Carries nothing. It cannot: a menu must be opened server-side, and the only thing the server
 * needs to know is which player asked, which the payload context already tells it. Sending a slot
 * index or a player id would be data the server must then distrust.
 */
public record OpenToolSlotsPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenToolSlotsPayload> TYPE =
        new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, "open_tool_slots"));

    public static final StreamCodec<ByteBuf, OpenToolSlotsPayload> STREAM_CODEC =
        StreamCodec.unit(new OpenToolSlotsPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
