package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.content.ToolSwapper;
import com.flatts.flattsthings.network.FTPayloads;
import com.flatts.flattsthings.network.ToggleAutoSwapPayload;
import com.flatts.flattsthings.registry.FTAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server side of the Z key, which had no coverage at all.
 *
 * <p>Everything the key protects was already pinned one layer down, on {@code ToolSwapper}: the
 * preference flips, and the pack's off wins. What nothing reached was the handler joining the packet
 * to those calls - so a handler that dropped its guard entirely would have passed the whole suite,
 * because the invariant it protects is separately enforced in {@code ToolSwapper.swapping}. What
 * would actually have broken is the thing a player sees: on a pack with the feature off, the key
 * would silently claim to have turned the swap on.
 *
 * <p><b>What these cannot see is the message itself.</b> {@code sendOverlayMessage} puts a packet on
 * a connection, and a mock player's connection goes nowhere a test can read. The three strings are
 * pinned for existence and translation by {@code ActionBarMessagesTest} in the unit layer; which of
 * them is chosen is only observable in a real client. Said plainly rather than papered over.
 */
final class ToggleAutoSwapPayloadTests {

    private ToggleAutoSwapPayloadTests() {
    }

    static void register() {
        // A KEY PRESS FLIPS THE PLAYER'S OWN PREFERENCE, through the handler rather than through
        // ToolSwapper.toggleWanted, which is what every other test in this repo calls.
        FTGameTests.test("a_key_press_flips_the_preference", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(player.getData(FTAttachments.AUTO_SWAP_WANTED),
                "premise: a player starts wanting the swap");

            FakePayloadContext context = new FakePayloadContext(player);
            FTPayloads.onToggleAutoSwap(new ToggleAutoSwapPayload(), context);

            helper.assertFalse(player.getData(FTAttachments.AUTO_SWAP_WANTED),
                "one press should have turned it off");

            FTPayloads.onToggleAutoSwap(new ToggleAutoSwapPayload(), context);
            helper.assertTrue(player.getData(FTAttachments.AUTO_SWAP_WANTED),
                "and a second press should turn it back on");
            helper.succeed();
        });

        // THE WORK IS DEFERRED RATHER THAN DONE ON THE NETWORK THREAD. A payload handler runs off the
        // main thread, and touching player data there is the kind of bug that shows up as a rare
        // crash on a busy server rather than as a failing test. The fake records that the handler
        // asked; it cannot prove the server would honour it, and nothing here pretends otherwise.
        FTGameTests.test("the_key_press_is_handled_on_the_main_thread", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            FakePayloadContext context = new FakePayloadContext(player);

            FTPayloads.onToggleAutoSwap(new ToggleAutoSwapPayload(), context);

            helper.assertTrue(context.enqueued(),
                "the handler should have deferred its work through enqueueWork");
            helper.succeed();
        });

        // THE PLAYER COMES FROM THE CONTEXT, WHICH IS THE AUTHORISATION. The payload carries nothing,
        // deliberately, so the only player a press can affect is the one who sent it. A handler that
        // reached for a player some other way would be a way to flip somebody else's setting.
        FTGameTests.test("a_key_press_only_touches_the_sender", 20, helper -> {
            ServerPlayer presser = helper.makeMockServerPlayerInLevel();
            ServerPlayer bystander = helper.makeMockServerPlayerInLevel();

            FTPayloads.onToggleAutoSwap(new ToggleAutoSwapPayload(),
                new FakePayloadContext(presser));

            helper.assertFalse(presser.getData(FTAttachments.AUTO_SWAP_WANTED),
                "the sender's preference should have flipped");
            helper.assertTrue(bystander.getData(FTAttachments.AUTO_SWAP_WANTED),
                "and nobody else's should have");
            helper.succeed();
        });

        // THE PACK'S OFF WINS, AND THE KEY DOES NOT PRETEND OTHERWISE. This is the branch that had
        // never run. ToolSwapper.swapping already makes the pack's switch win whatever the player
        // wants, so deleting this guard breaks nothing a test could see - except that the key would
        // then report "on" to somebody whose pack has the feature switched off, and quietly change a
        // stored preference they were told does not apply.
        FTGameTests.test("the_key_does_not_flip_a_swap_the_pack_switched_off", 20,
            FTGameTests.aloneIn("the_key_does_not_flip_a_swap_the_pack_switched_off"),
            helper -> {
                ServerPlayer player = helper.makeMockServerPlayerInLevel();
                try {
                    FTConfig.switchFor(FTConfig.TOOL_AUTO_SWAP).set(false);
                    helper.assertFalse(ToolSwapper.swapping(player),
                        "premise: the pack switch is off");

                    FTPayloads.onToggleAutoSwap(new ToggleAutoSwapPayload(),
                        new FakePayloadContext(player));

                    helper.assertTrue(player.getData(FTAttachments.AUTO_SWAP_WANTED),
                        "the preference must be left exactly as it was, because the key reported"
                            + " that the feature is unavailable rather than toggling anything");
                } finally {
                    FTConfig.switchFor(FTConfig.TOOL_AUTO_SWAP).set(true);
                }
                helper.succeed();
            });
    }
}
