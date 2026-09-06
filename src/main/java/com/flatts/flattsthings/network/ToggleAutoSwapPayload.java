package com.flatts.flattsthings.network;

import com.flatts.flattsthings.FlattsThings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * "Flip my auto-swap", sent when the player presses the key.
 *
 * <p><b>Carries nothing, deliberately - not even the new value.</b> Sending the state the client
 * thinks it wants means trusting a client to tell the server what its own setting is, and two
 * presses arriving out of order would leave the two sides disagreeing. A bare "flip it" cannot
 * disagree: the server owns the value, flips it, and says what it is now.
 */
public record ToggleAutoSwapPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleAutoSwapPayload> TYPE =
        new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, "toggle_auto_swap"));

    public static final StreamCodec<ByteBuf, ToggleAutoSwapPayload> STREAM_CODEC =
        StreamCodec.unit(new ToggleAutoSwapPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
