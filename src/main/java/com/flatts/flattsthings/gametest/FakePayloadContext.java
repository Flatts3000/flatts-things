package com.flatts.flattsthings.gametest;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Just enough of an {@link IPayloadContext} to run a payload handler in a GameTest.
 *
 * <p>The server side of the auto-swap keybind had no coverage at all, because the only thing that
 * calls it is the network layer delivering a packet and a headless test has no client to send one.
 * The issue that filed this guessed the fake would need nineteen members; the interface has
 * <b>seven</b> abstract methods and the rest are defaults, which is why this is thirty lines rather
 * than a refactor.
 *
 * <p><b>Everything not needed throws rather than returning null.</b> A fake that quietly answers
 * nothing turns "the handler reached for something it should not have" into a passing test with a
 * NullPointerException hiding somewhere downstream. If a handler ever needs one of these, the failure
 * says so by name.
 *
 * <p><b>{@code enqueueWork} runs inline</b>, which is a real difference from the server and is the
 * point: the handler is written to defer its work to the main thread, and running it now is what lets
 * a test see the result on the same tick. What this therefore does NOT prove is that the deferral
 * happens at all - {@link #enqueued} records that the handler asked, which is the closest a fake can
 * honestly get.
 */
final class FakePayloadContext implements IPayloadContext {

    private final Player player;

    /** Whether the handler deferred its work rather than doing it on the network thread. */
    private boolean enqueued;

    FakePayloadContext(Player player) {
        this.player = player;
    }

    boolean enqueued() {
        return this.enqueued;
    }

    @Override
    public Player player() {
        return this.player;
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        this.enqueued = true;
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        this.enqueued = true;
        return CompletableFuture.completedFuture(task.get());
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.SERVERBOUND;
    }

    @Override
    public ICommonPacketListener listener() {
        throw new UnsupportedOperationException(
            "a payload handler under test reached for the packet listener; if that is intended, this"
                + " fake needs to grow a real one rather than a null");
    }

    @Override
    public void handle(CustomPacketPayload payload) {
        throw new UnsupportedOperationException(
            "a payload handler under test tried to handle a nested payload: " + payload);
    }

    @Override
    public void finishCurrentTask(ConfigurationTask.Type type) {
        throw new UnsupportedOperationException(
            "a payload handler under test finished a configuration task: " + type);
    }
}
