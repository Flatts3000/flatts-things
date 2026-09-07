package com.flatts.flattsthings.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The three things the auto-swap key says, and whether they are words.
 *
 * <p>The key answers on the action bar every time it is pressed, on the argument that a key which
 * silently changes an invisible setting is one players press twice and then distrust. That argument
 * only holds if the strings are translated: an untranslated one renders as
 * {@code message.flattsthings.auto_swap.on} above the hotbar, which is worse than saying nothing.
 * Nothing else here would see it - a GameTest is a server-state oracle and never looks at a screen,
 * which is the same blind spot {@code KeyCategoryTest} exists for.
 *
 * <p><b>The set is asserted, not just the members.</b> Checking only that each expected key is
 * present would pass a rename that left the old entry behind, which is how a lang file ends up
 * carrying strings nothing displays. What this still cannot catch is a key renamed in
 * {@code FTPayloads} alone, because the handler's literals are not reachable from here; that half is
 * the reason the names are written out below rather than derived.
 */
class ActionBarMessagesTest {

    private static final List<String> EXPECTED = List.of(
        "message.flattsthings.auto_swap.off",
        "message.flattsthings.auto_swap.on",
        "message.flattsthings.auto_swap.unavailable");

    @Test
    void everyMessageTheKeyCanShowIsTranslated() {
        JsonObject lang = lang();
        var found = new TreeSet<String>();
        for (String key : lang.keySet()) {
            if (key.startsWith("message.flattsthings.")) {
                found.add(key);
            }
        }
        assertEquals(EXPECTED, List.copyOf(found),
            "the messages in en_us.json are not the ones the auto-swap key sends");
        for (String key : EXPECTED) {
            assertTrue(!lang.get(key).getAsString().isBlank(),
                key + " is translated to nothing, so the key would report by flashing an empty bar");
        }
    }

    private static JsonObject lang() {
        var stream = ActionBarMessagesTest.class.getResourceAsStream(
            "/assets/flattsthings/lang/en_us.json");
        assertTrue(stream != null, "en_us.json is not on the classpath");
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
            .getAsJsonObject();
    }
}
