package com.flatts.flattsthings.content.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A pressure plate only a player can press.
 *
 * <p>Vanilla ships two sensitivities, and the block does not choose either - the {@link BlockSetType}
 * does. Wood detects EVERYTHING (any entity, dropped items and arrows included) and stone detects
 * MOBS (any LivingEntity). Neither can express "a player, and nothing else", which is the one a
 * door, a shop or a trapped corridor actually wants: a stone plate is opened by a wandering cow,
 * and a wooden one by an arrow you shot at it.
 *
 * <p>This extends {@link BasePressurePlateBlock} rather than the concrete
 * {@link net.minecraft.world.level.block.PressurePlateBlock} for a mechanical reason, not a
 * stylistic one. The only behaviour that needs to change is {@link #getSignalStrength}, but every
 * block must also declare a {@code codec()}, and PressurePlateBlock declares its return type as
 * {@code MapCodec<PressurePlateBlock>}. Generics are invariant, so an override returning
 * {@code MapCodec<PlayerPressurePlateBlock>} cannot compile against it. The abstract parent
 * declares {@code MapCodec<? extends BasePressurePlateBlock>}, which accepts one.
 *
 * <p>Everything else is deliberately vanilla: the 20-tick hold, the click sounds off the block set
 * type, the game events, and a redstone output of 15. The sensitivity is the whole feature.
 */
public class PlayerPressurePlateBlock extends BasePressurePlateBlock {

    public static final MapCodec<PlayerPressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            BlockSetType.CODEC.fieldOf("block_set_type").forGetter(b -> b.type),
            propertiesCodec()
        ).apply(i, PlayerPressurePlateBlock::new));

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PlayerPressurePlateBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(properties, type);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public MapCodec<PlayerPressurePlateBlock> codec() {
        return CODEC;
    }

    /**
     * The feature, in one line: count players rather than entities or living entities.
     *
     * <p>{@code getEntityCount} supplies the two exclusions every vanilla plate already makes: it
     * drops spectators, and it drops anything answering true to {@code isIgnoringBlockTriggers()}.
     * The second is a narrower filter than it sounds - in 26.1 only Display, Interaction, Marker
     * and OminousItemSpawner override it - so note what is NOT excluded: a creative-mode player
     * presses this plate exactly like a survival one. That is deliberate, and it matches every
     * vanilla plate; a build being tested in creative behaves the way it will when played.
     */
    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        return getEntityCount(level, TOUCH_AABB.move(pos), Player.class) > 0 ? 15 : 0;
    }

    @Override
    protected int getSignalForState(BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected BlockState setSignalForState(BlockState state, int signal) {
        return state.setValue(POWERED, signal > 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
