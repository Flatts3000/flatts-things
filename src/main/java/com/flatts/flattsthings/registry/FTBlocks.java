package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.block.PlayerPressurePlateBlock;
import com.flatts.flattsthings.content.terrain.DirtTerrainSlabBlock;
import com.flatts.flattsthings.content.terrain.FallingTerrainSlabBlock;
import com.flatts.flattsthings.content.terrain.GrassTerrainSlabBlock;
import com.flatts.flattsthings.content.terrain.MudSlabBlock;
import com.flatts.flattsthings.content.terrain.RootedDirtSlabBlock;
import com.flatts.flattsthings.content.terrain.SnowyTerrainSlabBlock;
import com.flatts.flattsthings.content.terrain.SpreadingTerrainSlabBlock;
import com.flatts.flattsthings.content.terrain.TerrainSlabBlock;
import com.flatts.flattsthings.content.woodcutter.WoodcutterBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry.
 *
 * <p>Uses the factory form ({@code registerBlock(name, factory, propsSupplier)}) because MC 26.1
 * sets the {@code ResourceKey} on Properties before the block constructor runs; the older
 * {@code new Block(props)} form breaks.
 */
public final class FTBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(FlattsThings.MOD_ID);

    /**
     * The woodcutter: a saw bench for planks.
     *
     * <p>Properties copied from the crafting table rather than the stonecutter, and rather than
     * restated. It is a wooden bench, so it should burn, chop with an axe and sound like wood - all
     * of which come along with the copy. {@code ofLegacyCopy} rather than {@code ofFullCopy} for the
     * usual reason: the full copy also carries {@code drops} and {@code descriptionId}, which would
     * leave this rolling the crafting table's loot table under the crafting table's name.
     */
    public static final DeferredBlock<Block> WOODCUTTER = BLOCKS.registerBlock(
        "woodcutter",
        WoodcutterBlock::new,
        () -> BlockBehaviour.Properties.ofLegacyCopy(Blocks.CRAFTING_TABLE));

    /**
     * One player-only counterpart to one vanilla pressure plate.
     *
     * @param material the vanilla prefix, so {@code oak} gives {@code oak_player_pressure_plate}
     * @param setType  the vanilla plate's own block set type, which carries the click sounds
     * @param vanilla  the plate this one copies its properties from and is crafted out of
     */
    public record PlateVariant(String material, BlockSetType setType, Block vanilla) {
        public String blockId() {
            return material + "_player_pressure_plate";
        }
    }

    /**
     * Every vanilla {@code PressurePlateBlock}, in creative-tab order: the two stone-likes, then
     * the woods in vanilla's own wood order.
     *
     * <p><b>The two weighted plates are deliberately absent.</b> {@code light_weighted_pressure_plate}
     * and {@code heavy_weighted_pressure_plate} are a different block class that counts dropped item
     * stacks and outputs a proportional signal. "Player-only" has no meaning for a block whose whole
     * function is weighing items, so a counterpart would be a new feature wearing a familiar name.
     *
     * <p>This list is mirrored by {@code tools/plate_variants.py}, which generates the resources.
     * Two tests close that gap from both ends - see {@code PlayerPressurePlateTests} for the one
     * that catches a plate missing HERE, and {@code RegistryCompletenessTests} for the one that
     * catches a plate missing THERE.
     */
    public static final List<PlateVariant> VARIANTS = List.of(
        new PlateVariant("stone", BlockSetType.STONE, Blocks.STONE_PRESSURE_PLATE),
        new PlateVariant("polished_blackstone", BlockSetType.POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE),
        new PlateVariant("oak", BlockSetType.OAK, Blocks.OAK_PRESSURE_PLATE),
        new PlateVariant("spruce", BlockSetType.SPRUCE, Blocks.SPRUCE_PRESSURE_PLATE),
        new PlateVariant("birch", BlockSetType.BIRCH, Blocks.BIRCH_PRESSURE_PLATE),
        new PlateVariant("jungle", BlockSetType.JUNGLE, Blocks.JUNGLE_PRESSURE_PLATE),
        new PlateVariant("acacia", BlockSetType.ACACIA, Blocks.ACACIA_PRESSURE_PLATE),
        new PlateVariant("cherry", BlockSetType.CHERRY, Blocks.CHERRY_PRESSURE_PLATE),
        new PlateVariant("dark_oak", BlockSetType.DARK_OAK, Blocks.DARK_OAK_PRESSURE_PLATE),
        new PlateVariant("pale_oak", BlockSetType.PALE_OAK, Blocks.PALE_OAK_PRESSURE_PLATE),
        new PlateVariant("mangrove", BlockSetType.MANGROVE, Blocks.MANGROVE_PRESSURE_PLATE),
        new PlateVariant("bamboo", BlockSetType.BAMBOO, Blocks.BAMBOO_PRESSURE_PLATE),
        new PlateVariant("crimson", BlockSetType.CRIMSON, Blocks.CRIMSON_PRESSURE_PLATE),
        new PlateVariant("warped", BlockSetType.WARPED, Blocks.WARPED_PRESSURE_PLATE));

    /** Registered plates by material, in {@link #VARIANTS} order. */
    public static final Map<String, DeferredBlock<PlayerPressurePlateBlock>> PLATES =
        new LinkedHashMap<>();

    static {
        for (PlateVariant variant : VARIANTS) {
            PLATES.put(variant.material(), BLOCKS.registerBlock(
                variant.blockId(),
                props -> new PlayerPressurePlateBlock(variant.setType(), props),
                () -> propertiesOf(variant.vanilla())));
        }
    }

    /**
     * What behaviour a terrain family needs on top of being a slab.
     *
     * <p>Every one of these is a property of the MATERIAL rather than of the shape, and that split
     * is what makes this feature small: vanilla's {@code SlabBlock} handles the half-block half and
     * is subclassable, so only the material half is written here.
     */
    public enum SlabKind {
        /** Nothing beyond being a slab: coarse dirt, clay. */
        PLAIN,
        /** Greens itself next to vanilla grass or mycelium. Dirt, and only dirt. */
        DIRT,
        /** Spreads to nearby dirt and dies back when covered. Mycelium. */
        SPREADING,
        /** Spreading, and takes bonemeal. Grass, which is the only family that does both. */
        GRASS,
        /** Falls when unsupported, and lands the right way up. Gravel and the two sands. */
        FALLING,
        /** Carries the snowy blockstate. Podzol is the only one of the ten. */
        SNOWY,
        /** Grows hanging roots below when bonemealed. Rooted dirt. */
        ROOTED,
        /** Two pixels short, so you sink in. Mud. */
        MUD
    }

    /**
     * One terrain slab.
     *
     * @param family  the vanilla block id, so {@code coarse_dirt} gives {@code coarse_dirt_slab}
     * @param vanilla the block this copies its properties from and is crafted out of
     * @param kind    the material behaviour it needs beyond being a slab
     */
    public record TerrainSlabVariant(String family, Block vanilla, SlabKind kind) {
        public String blockId() {
            return family + "_slab";
        }
    }

    /**
     * The terrain families that get a slab, in creative-tab order (owner ruling, 2026-09-07, on the
     * block-variants issue: the terrain set only, slabs only, one switch).
     *
     * <p><b>Nine, not the twelve the ruling named.</b> Snow blocks came out for a reason a test
     * found rather than a reason anyone predicted: three of them in a row is already vanilla's snow
     * LAYER recipe. Chasing that turned up the better argument - a snow layer is already a stackable
     * partial snow block, so a snow slab is a thing vanilla effectively has, and this mod's entry
     * test is that a thing should be something vanilla should plausibly have and does NOT.
     *
     * <p>And Grass and mycelium are absent on purpose: they
     * spread, die back and take the snowy state, and vanilla implements all of that in
     * {@code SpreadingSnowyBlock}, which no slab can extend because {@code SlabBlock} is already the
     * parent. Shipping them as models with none of the behaviour is exactly the decorative grass
     * slab that was rejected on the sibling issue, so they wait for a design of their own.
     *
     * <p>This list is mirrored by {@code tools/terrain_slab_variants.py}, and the same two tests
     * close the loop that close it for the plates: a registry walk catches a family missing from the
     * JAVA side, and {@code RegistryCompletenessTests} catches one missing from the GENERATOR side.
     */
    public static final List<TerrainSlabVariant> TERRAIN_SLABS = List.of(
        new TerrainSlabVariant("dirt", Blocks.DIRT, SlabKind.DIRT),
        new TerrainSlabVariant("grass_block", Blocks.GRASS_BLOCK, SlabKind.GRASS),
        new TerrainSlabVariant("mycelium", Blocks.MYCELIUM, SlabKind.SPREADING),
        new TerrainSlabVariant("coarse_dirt", Blocks.COARSE_DIRT, SlabKind.PLAIN),
        new TerrainSlabVariant("rooted_dirt", Blocks.ROOTED_DIRT, SlabKind.ROOTED),
        new TerrainSlabVariant("podzol", Blocks.PODZOL, SlabKind.SNOWY),
        new TerrainSlabVariant("mud", Blocks.MUD, SlabKind.MUD),
        new TerrainSlabVariant("clay", Blocks.CLAY, SlabKind.PLAIN),
        new TerrainSlabVariant("gravel", Blocks.GRAVEL, SlabKind.FALLING),
        new TerrainSlabVariant("sand", Blocks.SAND, SlabKind.FALLING),
        new TerrainSlabVariant("red_sand", Blocks.RED_SAND, SlabKind.FALLING));

    /** Registered terrain slabs by family, in {@link #TERRAIN_SLABS} order. */
    public static final Map<String, DeferredBlock<TerrainSlabBlock>> TERRAIN =
        new LinkedHashMap<>();

    static {
        for (TerrainSlabVariant variant : TERRAIN_SLABS) {
            TERRAIN.put(variant.family(), BLOCKS.registerBlock(
                variant.blockId(),
                props -> switch (variant.kind()) {
                    case PLAIN -> new TerrainSlabBlock(props);
                    case DIRT -> new DirtTerrainSlabBlock(props);
                    case SPREADING -> new SpreadingTerrainSlabBlock(props);
                    case GRASS -> new GrassTerrainSlabBlock(props);
                    case FALLING -> new FallingTerrainSlabBlock(props);
                    case SNOWY -> new SnowyTerrainSlabBlock(props);
                    case ROOTED -> new RootedDirtSlabBlock(props);
                    case MUD -> new MudSlabBlock(props);
                },
                () -> terrainPropertiesOf(variant)));
        }
    }

    /**
     * A terrain slab's properties, copied from its vanilla block and then given a random tick if the
     * behaviour needs one.
     *
     * <p><b>The dirt slab is the one that needs adding to.</b> Vanilla dirt does not random-tick,
     * because vanilla dirt has nothing to do; the dirt SLAB has to notice a grass block beside it,
     * because vanilla grass cannot notice the slab. Grass and mycelium already tick, so the copy
     * brings that along and this only has to cover dirt.
     */
    private static BlockBehaviour.Properties terrainPropertiesOf(TerrainSlabVariant variant) {
        BlockBehaviour.Properties props = propertiesOf(variant.vanilla());
        return variant.kind() == SlabKind.DIRT ? props.randomTicks() : props;
    }

    /** The slab for one terrain family. Throws rather than returning null - a typo here is a bug. */
    public static DeferredBlock<TerrainSlabBlock> terrainSlab(String family) {
        DeferredBlock<TerrainSlabBlock> block = TERRAIN.get(family);
        if (block == null) {
            throw new IllegalArgumentException("no terrain slab for family: " + family);
        }
        return block;
    }

    /**
     * Take the vanilla plate's properties wholesale rather than restating them.
     *
     * <p>Transcribing fourteen property chains by hand is how a variant ends up subtly wrong, and
     * the differences are not where you would guess: crimson and warped are the two woods that are
     * NOT {@code ignitedByLava}, because nether wood does not burn. Copying means every variant
     * matches its counterpart on map colour, hardness, instrument, flammability and push reaction,
     * and keeps matching if Mojang retunes one.
     *
     * <p><b>{@code ofLegacyCopy}, and NOT {@code ofFullCopy}, which would be a bug.</b> The "full"
     * copy also carries over {@code drops} and {@code descriptionId} - so every plate here would
     * roll the VANILLA plate's loot table and render under the VANILLA plate's name, and both would
     * look like a data problem rather than a code one. The legacy copy takes the behaviour and
     * leaves identity to be derived from this block's own registry key, which is exactly the split
     * wanted. It is deprecated for being a partial copy; partial is the point.
     */
    @SuppressWarnings("deprecation")
    private static BlockBehaviour.Properties propertiesOf(Block vanilla) {
        return BlockBehaviour.Properties.ofLegacyCopy(vanilla);
    }

    /** The plate for one material. Throws rather than returning null - a typo here is a bug. */
    public static DeferredBlock<PlayerPressurePlateBlock> plate(String material) {
        DeferredBlock<PlayerPressurePlateBlock> block = PLATES.get(material);
        if (block == null) {
            throw new IllegalArgumentException("no player pressure plate for material: " + material);
        }
        return block;
    }

    private FTBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
