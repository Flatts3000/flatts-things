package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.block.PlayerPressurePlateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry.
 *
 * <p>Uses the factory form ({@code registerBlock(name, factory, propsSupplier)}) because MC 26.1
 * sets the ResourceKey on Properties before the block constructor runs; the older
 * {@code new Block(props)} form breaks.
 */
public final class FTBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(FlattsThings.MOD_ID);

    /**
     * The Player Pressure Plate.
     *
     * <p>Properties are vanilla stone_pressure_plate verbatim, {@code noCollision} and the BASEDRUM
     * note-block instrument included, so it sits in the world exactly like the plate it is meant to
     * stand beside. {@link BlockSetType#STONE} carries the click sounds; its
     * {@code pressurePlateSensitivity()} is never consulted, because
     * {@link PlayerPressurePlateBlock} overrides the one method that would read it.
     */
    public static final DeferredBlock<PlayerPressurePlateBlock> PLAYER_PRESSURE_PLATE =
        BLOCKS.registerBlock(
            "player_pressure_plate",
            props -> new PlayerPressurePlateBlock(BlockSetType.STONE, props),
            () -> BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .forceSolidOn()
                .instrument(NoteBlockInstrument.BASEDRUM)
                .noCollision()
                .strength(0.5F)
                .sound(SoundType.STONE)
                .pushReaction(PushReaction.DESTROY));

    private FTBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
