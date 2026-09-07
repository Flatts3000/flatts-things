package com.flatts.flattsthings.registry;

import com.flatts.flattsthings.FlattsThings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Tag keys this mod defines. */
public final class FTTags {

    /**
     * What may go in a tool slot.
     *
     * <p><b>A tag rather than a list of classes, and that is the whole point.</b> Another mod's
     * pickaxe already carries {@code #minecraft:pickaxes}, so it fits these slots with no compat
     * patch and no knowledge of it here. A pack that wants a wrench or a bucket in there widens the
     * tag in a datapack rather than waiting for a release.
     */
    public static final TagKey<Item> TOOL_SLOT_VALID = TagKey.create(
        Registries.ITEM, Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, "tool_slot_valid"));

    /**
     * What a water cauldron will react to.
     *
     * <p>The dispatcher vanilla asks is built once at startup, but it tests {@code stack.is(tag)}
     * when the player clicks, and tag contents are data pack material - so this tag is the seam that
     * lets a pack add a cauldron conversion without any code. What each item BECOMES is the
     * {@code flattsthings:cauldron_transform} data map; this tag only decides what the cauldron
     * bothers to look at.
     */
    public static final TagKey<Item> CAULDRON_TRANSFORMABLE = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath(FlattsThings.MOD_ID, "cauldron_transformable"));

    private FTTags() {
    }
}
