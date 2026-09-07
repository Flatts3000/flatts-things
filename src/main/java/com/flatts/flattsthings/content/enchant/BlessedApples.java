package com.flatts.flattsthings.content.enchant;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;

/**
 * Enchanting a golden apple at a table turns it into an enchanted golden apple.
 *
 * <p><b>The item's own name is the design.</b> An enchanted golden apple is a golden apple that has
 * been enchanted; the recipe this replaces made one out of eight gold blocks and a fruit, which
 * produces the item without ever doing the thing it is named after. This uses the enchanting table
 * players already know rather than a gesture they would have to be taught.
 *
 * <p><b>Three pieces, and none of them is a mixin.</b>
 *
 * <ol>
 *   <li>A golden apple is not enchantable - {@code ItemStack.isEnchantable} needs the
 *       {@code minecraft:enchantable} component and vanilla's apple has none. {@link
 *       ModifyDefaultComponentsEvent} adds it.
 *   <li>The table only offers what some enchantment supports, so there is one data-driven
 *       enchantment whose only supported item is the golden apple, joined to
 *       {@code #minecraft:in_enchanting_table}. That tag merges, so no vanilla file is replaced.
 *   <li>Enchanting normally leaves the same item carrying an enchantment. {@link
 *       PlayerEnchantItemEvent} fires immediately after, and the apple is swapped for the real
 *       {@code minecraft:enchanted_golden_apple}.
 * </ol>
 *
 * <p>The third step could instead override {@code IItemExtension.applyEnchantments} - the hook
 * NeoForge added so a book becomes an enchanted book - but reaching that for a vanilla item needs a
 * mixin, and this repo has exactly one with a written reason for it. The event gets to the same
 * place without a second.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class BlessedApples {

    /** Registered by {@code data/flattsthings/enchantment/blessing.json}, which the tag then joins. */
    public static final ResourceKey<Enchantment> BLESSING = ResourceKey.create(
        Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, "blessing"));

    /**
     * How readily the apple takes to the table.
     *
     * <p>Ten is a diamond tool's enchantability. It feeds the level costs the three slots offer,
     * and combined with the enchantment's own minimum cost of thirty it means a bare table cannot
     * reach the offer at all - a full ring of bookshelves is the price of admission.
     */
    private static final int ENCHANTABILITY = 10;

    /** What the anvil charges to put a Blessing book on an apple. */
    private static final int ANVIL_COST = 1;

    private BlessedApples() {
    }

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.GOLDEN_APPLE,
            builder -> builder.set(DataComponents.ENCHANTABLE, new Enchantable(ENCHANTABILITY)));
    }

    /**
     * Swap the enchanted apple for the item it was always meant to become.
     *
     * <p>Fired from {@code EnchantmentMenu.clickMenuButton} right after the enchanted stack is put
     * back in the slot, and before the menu recomputes its offers - so replacing the slot here is
     * seen by everything that follows.
     *
     * <p><b>Guarded on the enchantment, not just the item.</b> Another mod could make golden apples
     * take an enchantment of its own; turning that into an enchanted golden apple would be this mod
     * quietly eating somebody else's feature.
     */
    @SubscribeEvent
    public static void onEnchant(PlayerEnchantItemEvent event) {
        // NO CONFIG CHECK HERE, and that is the point rather than an omission. The switch acts
        // where the enchantment is loaded: with it off, blessing.json never registers and the table
        // never offers it, so this handler is unreachable. Checking again here would be worse than
        // redundant - the config is editable at runtime while the enchantment is only removed on a
        // data pack reload, so a switch flipped mid-session would leave the offer standing, and
        // clickMenuButton takes the player's levels BEFORE this event and their lapis AFTER it. An
        // early return there charges somebody thirty levels for a golden apple carrying an inert
        // enchantment. Finish what the table started; the gate is upstream.
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ItemStack enchanted = event.getEnchantedItem();
        if (!enchanted.is(Items.GOLDEN_APPLE)) {
            return;
        }
        boolean blessed = event.getEnchantments().stream()
            .anyMatch(instance -> instance.enchantment().is(BLESSING));
        if (!blessed) {
            return;
        }
        // FINDS THE SLOT HOLDING THIS EXACT STACK rather than assuming vanilla's menu and slot 0.
        // Another mod's enchanting machine fires the same event, and an instanceof on
        // EnchantmentMenu would silently do nothing there - leaving a player who paid holding a
        // blessed-but-untransformed apple, with no way to tell why.
        AbstractContainerMenu menu = event.getEntity().containerMenu;
        for (Slot slot : menu.slots) {
            if (slot.getItem() == enchanted) {
                slot.set(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, enchanted.getCount()));
                return;
            }
        }
        FlattsThings.LOGGER.warn(
            "A golden apple was blessed in {} but no slot held it, so it could not be turned into an"
                + " enchanted golden apple. The player has paid for nothing; please report this.",
            menu.getClass().getName());
    }

    /**
     * A Blessing book, applied to a golden apple on an anvil, does the same thing.
     *
     * <p><b>This closes a dead end rather than adding a route.</b> A book is a special case in
     * enchanting: {@code isPrimaryItemFor} is {@code isPrimaryItem(stack) || stack.is(Items.BOOK)},
     * so a book bypasses {@code supported_items} entirely and can roll Blessing at the table. That
     * book was then worthless - applying it in an anvil gives a golden apple carrying an inert
     * enchantment, because nothing posts {@code PlayerEnchantItemEvent} outside the enchanting
     * table. Somebody pays thirty levels for a book and gets a trap.
     *
     * <p>Either the book had to stop being offered, which is not reachable without a mixin on a
     * NeoForge default method, or it had to work. It works.
     */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        if (!event.getLeft().is(Items.GOLDEN_APPLE) || !event.getRight().is(Items.ENCHANTED_BOOK)) {
            return;
        }
        ItemEnchantments carried = event.getRight().get(DataComponents.STORED_ENCHANTMENTS);
        if (carried == null || carried.keySet().stream().noneMatch(held -> held.is(BLESSING))) {
            return;
        }
        event.setOutput(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, event.getLeft().getCount()));
        // Vanilla's own book-application cost. The levels went on the book at the table; this is the
        // anvil's fee for putting it on, not a second price for the apple.
        event.setXpCost(ANVIL_COST);
        event.setMaterialCost(1);
    }
}
