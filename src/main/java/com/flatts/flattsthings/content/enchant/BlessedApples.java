package com.flatts.flattsthings.content.enchant;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
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
        if (event.getEntity().level().isClientSide() || !FTConfig.enchantedGoldenApple()) {
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
        if (event.getEntity().containerMenu instanceof EnchantmentMenu menu) {
            menu.getSlot(0).set(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, enchanted.getCount()));
        }
    }
}
