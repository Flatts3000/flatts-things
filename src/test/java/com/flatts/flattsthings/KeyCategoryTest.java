package com.flatts.flattsthings;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Key Binds screen shows names, not translation keys.
 *
 * <p>A key binding whose category or name has no translation still works perfectly and still opens
 * the screen; it just renders {@code key.category.flattsthings.flattsthings} where a name should be.
 * Nothing else in this suite would catch that, because no headless test looks at a screen - the same
 * blind spot the registry completeness sweep exists for.
 *
 * <p>The category key is DERIVED from {@link FlattsThings#KEY_CATEGORY} rather than written out
 * here, so this checks the identifier and the lang file against each other rather than checking a
 * copy against a copy. Vanilla builds the label with {@code id.toLanguageKey("key.category")}.
 */
class KeyCategoryTest {

    @Test
    void everyKeyBindStringIsTranslated() {
        JsonObject lang = lang();
        for (String key : List.of(
                FlattsThings.KEY_CATEGORY.toLanguageKey("key.category"),
                "key.flattsthings.toggle_auto_swap")) {
            assertTrue(lang.has(key),
                "en_us.json has no entry for " + key + ", so the Key Binds screen shows the raw key");
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is translated to nothing");
        }
    }

    private static JsonObject lang() {
        var stream = KeyCategoryTest.class.getResourceAsStream(
            "/assets/flattsthings/lang/en_us.json");
        assertTrue(stream != null, "en_us.json is not on the classpath");
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
            .getAsJsonObject();
    }
}
