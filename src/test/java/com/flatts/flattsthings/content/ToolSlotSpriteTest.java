package com.flatts.flattsthings.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import org.junit.jupiter.api.Test;

/**
 * The empty-slot outlines name sprites that actually exist.
 *
 * <p>Worth checking at all because a typo in one of these names fails nothing: it renders a
 * missing-texture square on a screen no headless test ever looks at, and the first person to find
 * out is whoever opens their inventory.
 *
 * <p><b>It reads the game's jar off disk, which is unusual, and the two ordinary ways do not
 * work.</b> {@code getResource} finds nothing from either test layer: a GameTest server runs against
 * a server-filtered jar with no client assets at all, and this JUnit layer's classpath does not
 * carry them either - checked, not assumed. The sprites are in the artifact the build already
 * downloaded, so this opens it.
 *
 * <p>If the artifact is missing it FAILS rather than skipping. A check that quietly passes when it
 * could not run is worse than no check, because the green tick still gets believed.
 */
class ToolSlotSpriteTest {

    /**
     * Absolute, from a system property set by the test task. A moddev JUnit run works out of
     * build/minecraft-junit rather than the project root, so a relative path looks in the wrong
     * place and reports the jar missing when it is right there.
     */
    private static final Path ARTIFACTS =
        Path.of(System.getProperty("flattsthings.projectDir", "."), "build", "moddev", "artifacts");

    @Test
    void everyEmptySlotOutlineIsARealSprite() throws IOException {
        Path jar = clientJar();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            SimpleContainer container = new SimpleContainer(ToolSlots.SIZE);
            int outlined = 0;
            for (int index = 0; index < ToolSlots.SIZE; index++) {
                Identifier icon = new ToolSlot(container, index).getNoItemIcon();
                if (icon == null) {
                    // The fifth slot is the free one and is blank on purpose. See ToolSlot.ICONS.
                    continue;
                }
                outlined++;
                String entry = "assets/" + icon.getNamespace() + "/textures/gui/sprites/"
                    + icon.getPath() + ".png";
                assertNotNull(zip.getEntry(entry),
                    "no such sprite: " + icon + " (looked for " + entry + " in " + jar + ")");
            }
            // Counted, so deleting every icon cannot pass this by having nothing left to check.
            assertEquals(4, outlined, "expected the four tool families to be named");
        }
    }

    private static Path clientJar() throws IOException {
        assertTrue(Files.isDirectory(ARTIFACTS),
            ARTIFACTS.toAbsolutePath() + " is missing; run ./gradlew build first");
        try (var files = Files.list(ARTIFACTS)) {
            List<Path> jars = files
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .filter(path -> !path.getFileName().toString().contains("sources"))
                .filter(path -> !path.getFileName().toString().contains("merged"))
                .toList();
            assertTrue(jars.size() == 1,
                "expected exactly one patched game jar in " + ARTIFACTS.toAbsolutePath()
                    + ", found " + jars);
            return jars.get(0);
        }
    }
}
