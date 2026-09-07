package com.flatts.flattsthings.content.equipment;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * A chestplate and an elytra on an anvil, so one chest slot does both jobs.
 *
 * <p><b>The result is the chestplate, not a new item, and that is the whole trick.</b> Gliding in
 * 26.1 is a marker component rather than a property of the elytra:
 * {@code LivingEntity.canGlideUsing} asks only whether the stack has {@code minecraft:glider} and
 * whether its {@code Equippable} slot matches the slot it is worn in. The elytra is an ordinary item
 * with 432 durability and {@code .component(DataComponents.GLIDER, Unit.INSTANCE)} - that line is
 * the whole of flight.
 *
 * <p>So the chestplate keeps being a chestplate. It renders as one, because it is one. It keeps its
 * armour value, its enchantments and its trim, because nothing copied them anywhere. And
 * {@code canGlide} scans every equipment slot, so wearing it in the chest slot is enough.
 *
 * <p><b>The issue this came from proposed the opposite</b> - a component on the ELYTRA carrying the
 * chestplate - and listed five decisions that mostly followed from that shape: which armour value
 * shows, how two durability pools interact, how enchantments from both sides merge, and a model per
 * combination. None of them exist here. Kept as a note rather than deleted, because the wrong design
 * was the plausible one and somebody will propose it again.
 *
 * <p><b>ONE DURABILITY POOL, THE CHESTPLATE'S, and this is the one real ruling</b> (2026-09-07).
 * Flight damage in 26.1 goes to whatever item is gliding -
 * {@code getItemBySlot(slotToDamage).hurtAndBreak(1, ...)} - so a gliding chestplate wears down as
 * you fly. The issue warned that merging the pools makes flight "effectively repairable", which is
 * true and is the price. It is not a straight buff: a netherite chestplate carries 592 durability
 * against the elytra's 432, so flight time goes UP, while the thing being consumed is now the armour
 * keeping you alive. Any other answer means reimplementing durability against the grain of the
 * component model. Overturn it by writing the reversal here, not by deleting this paragraph.
 *
 * <p><b>An anvil rather than a smithing table</b>, which the issue reasonably suggested. A smithing
 * recipe produces a fixed result item, and this needs "the stack you put in, plus one component" -
 * preserving an arbitrary chestplate's damage, enchantments and trim while adding to it. That is
 * what an anvil already does with two items, it is the pattern this repo has used once before, and
 * a smithing version would be a custom recipe class for the same outcome.
 *
 * <p><b>The elytra's own enchantments are destroyed with it</b>, and nothing merges them across.
 * Somebody putting in a Mending, Unbreaking III elytra loses that work, so the config comment and the
 * changelog say so rather than only saying the elytra is consumed. The chestplate is the item that
 * survives, so its enchantments are the ones that matter.
 *
 * <p><b>It does not come apart.</b> The elytra is consumed and there is nothing left to separate:
 * the result is a chestplate that glides, so "removing the elytra" would mean stripping a component
 * off a perfectly good chestplate to give back an item that no longer exists. Losing the elytra is
 * the price of the combination, which is what the idea this came from proposed anyway.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class ArmoredElytra {

    /**
     * The anvil's fee. One level, matching the enchanted golden apple's, because the expensive part
     * is the elytra itself rather than the joining of two things somebody already owns.
     */
    private static final int ANVIL_COST = 1;

    private ArmoredElytra() {
    }

    /**
     * <b>Gated here rather than upstream, unlike the enchanting-table feature.</b> That one is turned
     * off by the enchantment never loading, so its handler is unreachable and re-checking would be
     * worse than redundant. This one has no data behind it at all - the behaviour IS the handler - so
     * the switch has to be read here or it does nothing.
     */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        if (!FTConfig.armoredElytra()) {
            return;
        }
        ItemStack chestplate = event.getLeft();
        // THE VANILLA TAG, so a modded chestplate that joins it works with no compat patch and no
        // knowledge of it here. 26.1's copper chestplate is already in it, and was not in 1.21's.
        if (!chestplate.is(ItemTags.CHEST_ARMOR) || !event.getRight().is(Items.ELYTRA)) {
            return;
        }
        // ONE AT A TIME. Chest armour does not stack in vanilla, but a stack size is a COMPONENT in
        // 26.1 and a modded item in the tag can set it - and copy() keeps the count, so a stack of
        // sixty-four would have come back as sixty-four gliders for one elytra and one level. The
        // sibling handler on the enchanted golden apple shipped exactly this bug and had it found in
        // review; there is no excuse for it twice.
        if (chestplate.getCount() != 1) {
            return;
        }
        // AND THE GAME'S OWN GATE, NOT JUST THE TAG. canGlideUsing wants an Equippable whose slot
        // matches where the item is worn, so an item in this tag with no Equippable at all - or one
        // worn on the body, which is a real slot in 26.1 - would come out of the anvil having eaten
        // an elytra and a level, and would never glide. The tag says what a pack MEANT; this says
        // what the game will actually accept. Mirroring vanilla's check is the only way to be sure
        // the two agree.
        Equippable equippable = chestplate.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.CHEST) {
            return;
        }
        // ALREADY GLIDES, so there is nothing to sell them. Without this the anvil would offer the
        // same combination forever, taking an elytra each time for no change.
        if (chestplate.has(DataComponents.GLIDER)) {
            return;
        }

        ItemStack result = chestplate.copy();
        result.set(DataComponents.GLIDER, Unit.INSTANCE);

        // THE NAME FIELD HAS TO BE HONOURED HERE, because nothing else will. NeoForge fires this
        // event after vanilla has computed its own result and then REPLACES that result with
        // whatever setOutput was given - so vanilla's renaming, which it had already applied to the
        // stack being thrown away, is lost. Without this the text box silently does nothing for this
        // one combination, which is the sort of small wrongness a player blames the anvil for.
        //
        // Only a non-blank name that differs, and only for one extra level, which is vanilla's own
        // naming cost. An empty box is left alone rather than treated as "remove the name": vanilla
        // does clear it, but doing that here would mean somebody loses the name off a chestplate
        // they were only trying to make glide.
        int cost = ANVIL_COST;
        String typed = event.getName();
        if (typed != null && !typed.isBlank()
                && !typed.equals(chestplate.getHoverName().getString())) {
            result.set(DataComponents.CUSTOM_NAME, Component.literal(typed));
            cost += 1;
        }

        event.setOutput(result);
        event.setXpCost(cost);
        event.setMaterialCost(1);
    }
}
