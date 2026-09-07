package com.flatts.flattsthings.content.cauldron;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.config.FTConfig;
import com.flatts.flattsthings.registry.FTDataMaps;
import com.flatts.flattsthings.registry.FTTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCauldronInteractionEvent;

/**
 * Dip a stack in a water cauldron and it comes out as something else.
 *
 * <p><b>Concrete is the case that earns this.</b> Vanilla sets concrete powder only against a real
 * water FLUID - {@code ConcretePowderBlock.canSolidify} tests {@code FluidTags.WATER}, and a cauldron
 * has no fluid state at all, so a cauldron full of water does nothing to it today. The vanilla loop
 * is therefore: carry a bucket, place a source, place powder against it one block at a time, break
 * the source, move on. That is a chore with no decision in it, which is the kind of thing this mod
 * exists to delete.
 *
 * <p>Mud is the same shape one step further. Vanilla already turns dirt into mud with a water bottle
 * ({@code PotionItem.useOn}, on anything in {@code #minecraft:convertable_to_mud}), so the recipe is
 * established and this is only its bulk form.
 *
 * <p><b>This is vanilla's own hook, not an event of ours.</b> {@code AbstractCauldronBlock.useItemOn}
 * asks a {@code CauldronInteraction.Dispatcher}, and NeoForge's
 * {@code RegisterCauldronInteractionEvent} lets a mod put an entry in the water dispatcher. Going
 * through {@code UseItemOnBlockEvent} instead would have worked and would have run this mod's code on
 * every right click on every block in the game to answer "no".
 *
 * <p><b>Registered against a TAG, which is what makes it extensible without code.</b> The dispatcher
 * is built once at startup, but it resolves {@code itemStack.is(tag)} at interaction time, and tag
 * contents come from data packs. So a pack adds its item to
 * {@code #flattsthings:cauldron_transformable} and an entry to the {@code cauldron_transform} data
 * map, and it works with nothing registered here.
 *
 * <p><b>A tag entry SHADOWS a vanilla item interaction rather than sitting beside it, and the first
 * version of this file claimed the opposite.</b> {@code Dispatcher.get} walks its tag map first and
 * returns on the first hit, consulting the per-item map only if no tag matched - so any item in this
 * tag routes here, and there is no way to hand it back. Declining (no data map entry, or the feature
 * switched off) returns {@code TRY_WITH_EMPTY_HAND}; it does NOT fall through to whatever vanilla
 * had registered for that item.
 *
 * <p>Nothing shipped is affected: concrete powder and dirt have no vanilla cauldron interaction, so
 * with this feature off the cauldron behaves exactly as vanilla's does. <b>But a pack must not add an
 * item that already has one</b> - putting a shulker box or a dyed leather item in this tag would stop
 * it being washed, and turning the feature off would not give that back. Vanilla registers
 * {@code #minecraft:cauldron_can_remove_dye} into the same map, and two tags matching one item
 * resolve out of a {@code HashMap} in no defined order.
 */
@EventBusSubscriber(modid = FlattsThings.MOD_ID)
public final class CauldronTransforms {

    /** Vanilla's dispatcher id for a water-filled cauldron. */
    private static final Identifier WATER = Identifier.withDefaultNamespace("water");

    private CauldronTransforms() {
    }

    @SubscribeEvent
    public static void register(RegisterCauldronInteractionEvent.Interaction event) {
        event.register(WATER, FTTags.CAULDRON_TRANSFORMABLE, CauldronTransforms::transform);
    }

    /**
     * <b>The whole held stack converts, and one level of water pays for it.</b> Converting one item
     * per click would be slower than the vanilla loop this replaces, which would make the feature
     * pointless; a cauldron is a vessel, and the reason to walk to one is to do a stack at a time.
     *
     * <p>It is not free: three conversions empty a cauldron, and refilling is a bucket or a rainy
     * roof. Against vanilla's water source, which is infinite and converts unlimited powder for the
     * price of one bucket, this is the more expensive route and the less tedious one. The cost is
     * per conversion rather than per item on purpose - a stack-sized price would be arithmetic
     * nobody can see, since the cauldron has three levels and a stack is sixty-four.
     */
    private static InteractionResult transform(BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand,
                                               ItemStack itemInHand) {
        if (!FTConfig.cauldronTransforms()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        CauldronTransform transform =
            itemInHand.getItem().builtInRegistryHolder().getData(FTDataMaps.CAULDRON_TRANSFORM);
        // IN THE TAG BUT NOT IN THE MAP is a data pack half-finished, not a crash. The tag decides
        // what the cauldron reacts to and the map decides what it becomes, and a pack can add one
        // without the other.
        if (transform == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // NOT REACHABLE BY ANYTHING THIS MOD SHIPS, and kept anyway. Every shipped transform costs
        // one level and a water cauldron always has at least one, so this can only fire for a pack
        // that sets a water_cost above one. No test pins it for that reason - said plainly here
        // rather than left as a line coverage counts and nothing proves.
        if (state.getValue(LayeredCauldronBlock.LEVEL) < transform.waterCost()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (!level.isClientSide()) {
            Item used = itemInHand.getItem();
            if (player.hasInfiniteMaterials()) {
                // CREATIVE FOLLOWS VANILLA'S RULE HERE, NOT THIS FEATURE'S.
                // ItemUtils.createFilledResult, which vanilla's own bottle filling uses, does not
                // consume a creative player's input and hands over a result only if they have none.
                // Doing the survival thing instead would eat a stack that is supposed to be free,
                // for no gain: a creative player has the result in the tab already.
                //
                // AN EARLIER VERSION OF THIS COMMENT SAID Inventory.add "reports success for a
                // creative player and stores nothing", and that is not what 26.1 does. It zeroes the
                // stack and returns true only when the loop made no progress at all - with room it
                // stores normally through addResource. The branch is right; the reason given for it
                // was wrong, which is worse than no reason because the next person builds on it.
                ItemStack single = transform.resultFor(1);
                if (!player.getInventory().contains(single)) {
                    player.getInventory().add(single);
                }
            } else {
                // THE HAND IS EMPTIED FIRST, AND THE ORDER IS LOAD-BEARING. Inventory.add looks for a
                // slot with room and then a free slot; while the input is still sitting in the
                // selected slot, that slot is neither. A player with a full inventory converting the
                // stack they are holding would have had the whole result thrown on the floor, into
                // the one slot it fits in perfectly. Pinned by
                // a_full_inventory_still_gets_the_result.
                ItemStack result = transform.resultFor(itemInHand.getCount());
                itemInHand.setCount(0);
                give(player, result);
            }
            for (int paid = 0; paid < transform.waterCost(); paid++) {
                // Re-read: each call rewrites the state, and the last one replaces the block with an
                // empty cauldron, which has no LEVEL property to read on the next pass.
                BlockState current = level.getBlockState(pos);
                if (current.hasProperty(LayeredCauldronBlock.LEVEL)) {
                    LayeredCauldronBlock.lowerFillLevel(current, level, pos);
                }
            }
            // VANILLA'S STATISTICS, because "Interact with Cauldron" counting every cauldron use
            // except this one is the sort of parity gap nobody reports and everybody notices.
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(used));

            level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
            // FLUID_PICKUP, NOT FLUID_PLACE, and vanilla is the authority on which. Its glass bottle
            // fires PICKUP after lowering the level and its water potion fires PLACE after raising
            // it. This takes water out, so it is a pickup - and a calibrated sculk sensor tuned from
            // watching a cauldron get filled should not hear this as the opposite thing.
            // Only when water actually moved: a pack-defined water_cost of zero moves none.
            if (transform.waterCost() > 0) {
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }
            ((ServerLevel) level).sendParticles(ParticleTypes.SPLASH,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.2, 0.0, 0.2, 1.0);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Hand over a result that may be bigger than a stack, and never delete it.
     *
     * <p>Nothing this mod ships converts more than one-for-one, so a full stack in is a full stack
     * out and this is one pass. A pack that sets a {@code count} above one can ask for more than
     * sixty-four at once, though, and an oversized {@code ItemStack} is not a thing the inventory
     * handles gracefully - so it is split here rather than trusted to.
     */
    private static void give(Player player, ItemStack result) {
        int remaining = result.getCount();
        while (remaining > 0) {
            ItemStack chunk = result.copy();
            chunk.setCount(Math.min(remaining, result.getMaxStackSize()));
            remaining -= chunk.getCount();
            if (!player.getInventory().add(chunk)) {
                player.drop(chunk, false);
            }
        }
    }
}
