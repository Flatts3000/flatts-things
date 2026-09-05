package com.flatts.flattsthings.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.flattsthings.registry.FTBlocks.PlateVariant;
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

    @Test
    void plateReturnsTheBlockForAKnownMaterial() {
        assertNotNull(FTBlocks.plate("stone"), "stone is in the variant table and must resolve");
        assertNotNull(FTBlocks.plate("warped"), "warped is in the variant table and must resolve");
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

    @Test
    void everyVariantIsRegisteredAndReachableByItsMaterial() {
        assertEquals(FTBlocks.VARIANTS.size(), FTBlocks.PLATES.size(),
            "every variant in the table must have produced a registered block");
        for (PlateVariant variant : FTBlocks.VARIANTS) {
            assertNotNull(FTBlocks.plate(variant.material()),
                variant.material() + " is in the table but not reachable through plate()");
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
