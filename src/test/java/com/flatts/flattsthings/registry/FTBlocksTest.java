package com.flatts.flattsthings.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.flattsthings.FlattsThings;
import com.flatts.flattsthings.content.block.PlayerPressurePlateBlock;
import com.flatts.flattsthings.registry.FTBlocks.PlateVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The mod's first unit tests, and they are here rather than in a GameTest for the reason the
 * project file gives: no world, no rendering and no server means a GameTest is the wrong instrument
 * and a slower one.
 *
 * <p>These run through moddev's JUnit integration, so the registries are populated and
 * {@link FTBlocks#VARIANTS} is real rather than mocked.
 */
class FTBlocksTest {

    /**
     * Asserts the TYPE, not merely non-null.
     *
     * <p>The first version of this asserted {@code assertNotNull(FTBlocks.plate("stone"))}, which
     * has no reachable failing branch: {@code plate()} throws on a miss and never returns null, so
     * the assertion could only ever pass. Resolving the holder and checking what came back is the
     * claim actually worth making - a variant registered against the vanilla class instead of ours
     * would fire for cows and is exactly the mistake this catches at the unit layer.
     */
    @Test
    void plateResolvesToAPlayerPlateForAKnownMaterial() {
        assertInstanceOf(PlayerPressurePlateBlock.class, FTBlocks.plate("stone").get(),
            "stone must resolve to this mod's plate, not vanilla's");
        assertInstanceOf(PlayerPressurePlateBlock.class, FTBlocks.plate("warped").get(),
            "warped must resolve to this mod's plate, not vanilla's");
    }

    /**
     * The lookup fails loudly. It returned null before this test existed, which would surface as a
     * NullPointerException somewhere else entirely - most likely inside a registry callback during
     * mod construction, where the stack trace names nothing useful.
     */
    @Test
    void plateThrowsWithAUsefulMessageForAnUnknownMaterial() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> FTBlocks.plate("granite"));
        assertTrue(thrown.getMessage().contains("granite"),
            "the message must name the material that was asked for, got: " + thrown.getMessage());
    }

    /**
     * RESOLVES EVERY HOLDER, which is the difference between testing the map and testing the
     * registry.
     *
     * <p>{@code FTBlocks.PLATES} is filled by the class's static initializer the first time anything
     * touches it, whether or not the {@code DeferredRegister} was ever hooked to the mod bus. The
     * first version of this test only checked the map, so deleting
     * {@code FTBlocks.register(modEventBus)} from the {@code FlattsThings} constructor left it green
     * while the mod shipped with zero blocks. Calling {@code get()} is what forces the registry to
     * have been populated.
     */
    @Test
    void everyVariantIsActuallyRegistered() {
        assertEquals(FTBlocks.VARIANTS.size(), FTBlocks.PLATES.size(),
            "every variant in the table must have produced a holder");
        for (PlateVariant variant : FTBlocks.VARIANTS) {
            Block resolved = FTBlocks.plate(variant.material()).get();
            assertInstanceOf(PlayerPressurePlateBlock.class, resolved,
                variant.material() + " did not resolve to a player plate");
            assertEquals(
                Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, variant.blockId()),
                BuiltInRegistries.BLOCK.getKey(resolved),
                variant.material() + " is registered under an unexpected id");
        }
    }

    /**
     * A duplicated material would silently overwrite its twin in the LinkedHashMap, leaving one
     * fewer block than the table claims and no error anywhere.
     */
    @Test
    void materialsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (PlateVariant variant : FTBlocks.VARIANTS) {
            assertTrue(seen.add(variant.material()),
                "duplicate material in the variant table: " + variant.material());
        }
    }

    @Test
    void blockIdFollowsTheVanillaNamingOrder() {
        for (PlateVariant variant : FTBlocks.VARIANTS) {
            assertEquals(variant.material() + "_player_pressure_plate", variant.blockId(),
                "block ids are material-first, matching vanilla's own <material>_pressure_plate");
        }
    }
}
