package com.flatts.flattsthings.gametest;

import com.flatts.flattsthings.config.FTConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * A chestplate that glides.
 *
 * <p>The assertion that matters is not "the component is present" but
 * {@link LivingEntity#canGlideUsing}, which is the method the game itself calls every tick to decide
 * whether a player is flying. Asserting the component would pass just as green against a component
 * the game does not read.
 */
final class ArmoredElytraTests {

    private ArmoredElytraTests() {
    }

    static void register() {
        // THE WHOLE FEATURE, ASSERTED THROUGH THE GAME'S OWN QUESTION. canGlideUsing is what
        // LivingEntity.canGlide calls every tick, and it wants the glider component AND an Equippable
        // whose slot matches where the item is worn. A chestplate satisfies the second half already.
        FTGameTests.test("a_chestplate_and_an_elytra_make_something_that_glides", 20, helper -> {
            AnvilUpdateEvent event = new AnvilUpdateEvent(
                new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.ELYTRA), null,
                ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
            NeoForge.EVENT_BUS.post(event);
            ItemStack result = event.getOutput();

            helper.assertTrue(result.is(Items.NETHERITE_CHESTPLATE),
                "the result should still BE the chestplate, found " + result.getItem());
            helper.assertTrue(LivingEntity.canGlideUsing(result, EquipmentSlot.CHEST),
                "and the game's own check should say it glides in the chest slot");
            helper.assertTrue(event.getMaterialCost() == 1, "it should consume the elytra");
            helper.succeed();
        });

        // AND IT IS STILL ARMOUR. The point of the feature is that one slot does both jobs, so the
        // half that is easy to lose is the half nothing else would notice: an armour value and a
        // durability pool that still belong to a chestplate.
        FTGameTests.test("the_armoured_elytra_is_still_a_chestplate", 20, helper -> {
            ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
            AnvilUpdateEvent event = new AnvilUpdateEvent(plain.copy(), new ItemStack(Items.ELYTRA),
                null, ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
            NeoForge.EVENT_BUS.post(event);
            ItemStack result = event.getOutput();

            // ASSERTED AS EQUAL, NOT AS BOTH-PRESENT. The first version of this line compared two
            // null checks against each other, which is true when both are null and therefore true
            // when the armour has been stripped off both sides.
            helper.assertTrue(
                java.util.Objects.equals(result.get(DataComponents.ATTRIBUTE_MODIFIERS),
                    plain.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                "the armour modifiers should be exactly the chestplate's, untouched");
            helper.assertTrue(!plain.getAttributeModifiers().modifiers().isEmpty(),
                "premise: a diamond chestplate has armour modifiers to preserve in the first place");
            helper.assertTrue(result.getMaxDamage() == plain.getMaxDamage(),
                "and the durability pool should be the chestplate's " + plain.getMaxDamage()
                    + ", found " + result.getMaxDamage());
            helper.assertTrue(
                result.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.CHEST,
                "and it should still be worn on the chest");
            helper.succeed();
        });

        // WHAT WAS ALREADY ON THE CHESTPLATE COMES ALONG, which is the reason this is a component on
        // the chestplate rather than a new item. A worn, enchanted, named chestplate has to survive
        // the combination or nobody would use it on the one they actually wear.
        FTGameTests.test("an_enchanted_chestplate_keeps_everything_it_had", 20, helper -> {
            ItemStack worn = new ItemStack(Items.NETHERITE_CHESTPLATE);
            worn.setDamageValue(120);
            worn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Old Faithful"));

            AnvilUpdateEvent event = new AnvilUpdateEvent(worn, new ItemStack(Items.ELYTRA), null,
                ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
            NeoForge.EVENT_BUS.post(event);
            ItemStack result = event.getOutput();

            helper.assertTrue(result.getDamageValue() == 120,
                "the wear should come along, found " + result.getDamageValue());
            helper.assertTrue(result.get(DataComponents.CUSTOM_NAME) != null,
                "and so should the name");
            helper.succeed();
        });

        // A GLIDING CHESTPLATE STOPS GLIDING BEFORE IT BREAKS, which is vanilla's rule for an elytra
        // and comes along free because canGlideUsing asks nextDamageWillBreak. Worth pinning because
        // it is the difference between landing and falling out of the sky.
        FTGameTests.test("a_nearly_broken_armoured_elytra_stops_gliding", 20, helper -> {
            ItemStack result = new ItemStack(Items.NETHERITE_CHESTPLATE);
            result.set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE);
            result.setDamageValue(result.getMaxDamage() - 1);

            helper.assertFalse(LivingEntity.canGlideUsing(result, EquipmentSlot.CHEST),
                "one durability from breaking, it must not glide");
            helper.assertTrue(result.getMaxDamage() > 0, "premise: it is a damageable item");
            helper.succeed();
        });

        // THE ANVIL'S NAME BOX STILL WORKS, which it would not for free. NeoForge fires this event
        // after vanilla built its own result and then throws that result away in favour of ours, so
        // vanilla's renaming goes with it. A player typing a name and getting an unnamed item back
        // blames the anvil.
        FTGameTests.test("naming_it_in_the_anvil_works", 20, helper -> {
            AnvilUpdateEvent event = new AnvilUpdateEvent(
                new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.ELYTRA), "Wings",
                ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
            NeoForge.EVENT_BUS.post(event);

            helper.assertTrue(event.getOutput().get(DataComponents.CUSTOM_NAME) != null,
                "a name typed in the anvil should be on the result");
            helper.assertTrue(event.getOutput().getHoverName().getString().equals("Wings"),
                "and it should be the name typed, found "
                    + event.getOutput().getHoverName().getString());
            helper.assertTrue(event.getXpCost() > 1,
                "renaming costs a level on top, as it does in vanilla; cost was "
                    + event.getXpCost());
            helper.succeed();
        });

        // AND AN EMPTY BOX MUST NOT EAT A NAME. Vanilla clears a custom name when the field is blank;
        // doing that here would take the name off a chestplate somebody was only trying to make
        // glide, so a blank field is left alone and this pins that.
        FTGameTests.test("an_empty_name_box_leaves_the_name_alone", 20, helper -> {
            ItemStack named = new ItemStack(Items.NETHERITE_CHESTPLATE);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));

            AnvilUpdateEvent event = new AnvilUpdateEvent(named, new ItemStack(Items.ELYTRA), "",
                ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
            NeoForge.EVENT_BUS.post(event);

            helper.assertTrue(
                event.getOutput().getHoverName().getString().equals("Old Faithful"),
                "an empty name box should leave the existing name alone, found "
                    + event.getOutput().getHoverName().getString());
            helper.succeed();
        });

        // AND IT MUST NOT FIRE ON ANYTHING ELSE, without which a handler that ignored its inputs
        // would pass everything above while turning every anvil recipe into a glider.
        FTGameTests.test("the_elytra_handler_leaves_other_combinations_alone", 20, helper -> {
            ItemStack gliding = new ItemStack(Items.NETHERITE_CHESTPLATE);
            gliding.set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE);

            record Case(String what, ItemStack left, ItemStack right) {}
            List<Case> cases = List.of(
                new Case("an elytra on a helmet",
                    new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.ELYTRA)),
                new Case("an elytra on a pickaxe",
                    new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.ELYTRA)),
                new Case("a plain book on a chestplate",
                    new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.BOOK)),
                // The one that would quietly eat elytras forever: a chestplate that already glides
                // is still a chestplate, so without a guard the anvil keeps offering the same trade.
                new Case("an elytra on a chestplate that already glides",
                    gliding, new ItemStack(Items.ELYTRA)));

            List<String> wrong = new ArrayList<>();
            for (Case each : cases) {
                AnvilUpdateEvent event = new AnvilUpdateEvent(each.left(), each.right(), null,
                    ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
                NeoForge.EVENT_BUS.post(event);
                if (!event.getOutput().isEmpty()) {
                    wrong.add(each.what());
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "the anvil handler fired on combinations it should ignore: " + wrong);
            helper.succeed();
        });

        // THE SWITCH, in an environment of its own because the config is one global value.
        FTGameTests.test("switching_the_armoured_elytra_off_stops_the_anvil_offering_it", 20,
            FTGameTests.aloneIn("switching_the_armoured_elytra_off_stops_the_anvil_offering_it"),
            helper -> {
                try {
                    FTConfig.switchFor(FTConfig.ARMORED_ELYTRA).set(false);

                    AnvilUpdateEvent event = new AnvilUpdateEvent(
                        new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.ELYTRA), null,
                        ItemStack.EMPTY, 0, 0, helper.makeMockServerPlayerInLevel());
                    NeoForge.EVENT_BUS.post(event);

                    helper.assertTrue(event.getOutput().isEmpty(),
                        "with the feature off the anvil should offer nothing, offered "
                            + event.getOutput().getItem());
                } finally {
                    FTConfig.switchFor(FTConfig.ARMORED_ELYTRA).set(true);
                }
                helper.succeed();
            });
    }
}
