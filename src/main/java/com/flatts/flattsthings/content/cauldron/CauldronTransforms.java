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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
            ItemStack result = transform.resultFor(itemInHand.getCount());
            itemInHand.setCount(0);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
            for (int paid = 0; paid < transform.waterCost(); paid++) {
                // Re-read: each call rewrites the state, and the last one replaces the block with an
                // empty cauldron, which has no LEVEL property to read on the next pass.
                BlockState current = level.getBlockState(pos);
                if (current.hasProperty(LayeredCauldronBlock.LEVEL)) {
                    LayeredCauldronBlock.lowerFillLevel(current, level, pos);
                }
            }
            level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
            ((ServerLevel) level).sendParticles(ParticleTypes.SPLASH,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.2, 0.0, 0.2, 1.0);
        }
        return InteractionResult.SUCCESS;
    }
}
