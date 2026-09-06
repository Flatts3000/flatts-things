package com.flatts.flattsthings.content;

/**
 * Where the tool slots sit on the inventory screen.
 *
 * <p><b>Shared by the mixin that creates the slots and the renderer that draws behind them, because
 * they must agree exactly.</b> The slots are real {@code Slot}s in vanilla's {@code InventoryMenu},
 * so vanilla positions the items and handles the clicks from these numbers; the panel and the sunken
 * squares underneath are ours to paint. If the two ever disagree, items render over empty screen and
 * the click box is somewhere else - which looks like a rendering bug and is a coordinate bug.
 *
 * <p><b>Below the panel rather than inside it.</b> Vanilla's inventory is 176 wide and the free space
 * inside it fits three slots at most, so five cannot go in without moving vanilla's own widgets - and
 * moving another mod's or the game's widgets is how an inventory screen ends up broken for everyone
 * who added anything else. Below the hotbar is space nothing else claims.
 *
 * <p><b>Not the right-hand edge</b>, which is the other obvious place and is where JEI puts its item
 * list. This mod ships no JEI integration on purpose, so a right-hand strip would sit under someone
 * else's panel with no way to negotiate.
 */
public final class ToolSlotLayout {

    /** Vanilla's inventory panel, which these numbers are relative to. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    /** One slot, the vanilla measurement: 16 of item with a pixel of border each side. */
    public static final int SLOT = 18;

    /** The gap between the bottom of vanilla's panel and the top of ours. */
    public static final int GAP = 5;

    /**
     * The border around the slots.
     *
     * <p><b>Without it the panel is invisible.</b> Vanilla's {@code container/slot} sprite is 18x18
     * and fully opaque, and five of them tile the strip exactly, so a background drawn to the same
     * rectangle is painted over completely - every fill was dead paint and the strip had no edge at
     * all between the slots and the world behind them. Four pixels is what vanilla insets its own
     * slots by.
     */
    public static final int MARGIN = 4;

    /** Left edge of the strip's background, centred under the panel. */
    public static final int STRIP_X = (PANEL_WIDTH - (ToolSlots.SIZE * SLOT + MARGIN * 2)) / 2;

    /** Top edge of the strip's background. */
    public static final int STRIP_Y = PANEL_HEIGHT + GAP;

    /** The strip's full background, slots plus border. */
    public static int stripWidth() {
        return ToolSlots.SIZE * SLOT + MARGIN * 2;
    }

    public static int stripHeight() {
        return SLOT + MARGIN * 2;
    }

    /** Where slot {@code index} puts its 16x16 of item: inside the border, then inside its sprite. */
    public static int slotX(int index) {
        return STRIP_X + MARGIN + 1 + index * SLOT;
    }

    public static int slotY() {
        return STRIP_Y + MARGIN + 1;
    }

    private ToolSlotLayout() {
    }
}
