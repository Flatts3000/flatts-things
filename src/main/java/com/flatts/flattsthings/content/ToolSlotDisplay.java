package com.flatts.flattsthings.content;

/**
 * Whether the screen currently being drawn is one that has laid the tool slots out.
 *
 * <p><b>This exists because our slots are appended to vanilla's {@code InventoryMenu}, and anything
 * else that draws that menu positions them itself.</b> The creative inventory does exactly that:
 * {@code CreativeModeInventoryScreen.selectTab} walks every slot of {@code player.inventoryMenu} and
 * places it from its index, sending everything at index 36 or above to the hotbar row. Ours are 46 to
 * 50, so they landed on top of hotbar slots one to five and drew their silhouettes there - while
 * {@code ToolSlotStrip}, which correctly refuses to paint on anything that is not an
 * {@code InventoryScreen}, left no panel behind them.
 *
 * <p><b>The overlap was in DRAWING ONLY, and an earlier version of this note said "and were live to
 * clicks", which is wrong.</b> {@code AbstractContainerScreen.getHoveredSlot} returns the FIRST
 * active slot under the cursor in list order, and the creative screen's own hotbar wrappers sit at
 * exactly those coordinates at indices 37 to 41 - earlier than ours - so a click always went to the
 * hotbar. Corrected rather than left, because a wrong reason in a comment is what the next person
 * reasons from.
 *
 * <p><b>Repositioning them is not available: {@code Slot.x} and {@code Slot.y} are final in 26.1.</b>
 * Vanilla parks its own crafting slots off-screen by CONSTRUCTING wrappers at -2000, which is a thing
 * only the screen building the list can do. What is left to us is {@link ToolSlot#isActive()}, which
 * the game consults for drawing the slot, for drawing its empty-slot icon, and in {@code findSlot} -
 * so a slot that answers false is invisible and unclickable wherever it has been put.
 *
 * <p><b>The rule is fail-safe rather than fail-broken, and that is the point.</b> The bug was not
 * "the creative screen is unhandled"; it was that an unhandled screen showed the slots anyway. A
 * screen is opted IN by painting the strip for it, and anything else - another mod's inventory
 * screen, a vanilla screen that does not exist yet - gets nothing rather than five silhouettes in
 * somebody else's hotbar.
 *
 * <p><b>Written by the client, never read by the server.</b> {@code isActive} is a client decision
 * and nothing server-side consults it, so this flag is only ever meaningful in a client process. It
 * defaults to true so that a context which never sets it behaves exactly as before.
 */
public final class ToolSlotDisplay {

    private static volatile boolean shown = true;

    private ToolSlotDisplay() {
    }

    /** Whether the tool slots should draw and take clicks right now. */
    public static boolean shown() {
        return shown;
    }

    /**
     * Set from the client while a screen is being drawn.
     *
     * <p>Set every frame from the render hook rather than once when a screen opens, deliberately: the
     * creative inventory rebuilds its slot list when the tab changes WITHOUT reopening the screen, so
     * a value latched at open time would be stale exactly when it matters.
     *
     * <p><b>And cleared when any screen initialises</b>, which closes the other end. The render hook
     * only speaks for screens that actually draw a background: a mod cancelling the cancellable
     * {@code ScreenEvent.Render.Pre} suppresses the whole extract, so without this the flag would
     * still read {@code true} from the last inventory frame and the slots would draw wherever that
     * screen had put them - the original bug, in exactly the "another mod's inventory screen" case
     * this is supposed to cover. A new screen starts hidden and has to earn its way back.
     *
     * <p>The initial value stays {@code true} rather than {@code false}, because a context with no
     * client never sets this at all and {@code isActive} is meaningless there - a server has no
     * screen to have laid anything out. Flipping the default would make the server-side tests that
     * assert the CONFIG gate start asserting a client concern instead.
     */
    public static void setShown(boolean value) {
        shown = value;
    }
}
