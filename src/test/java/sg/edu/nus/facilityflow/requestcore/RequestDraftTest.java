package sg.edu.nus.facilityflow.requestcore;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestDraftTest {
    private final String category = UUID.randomUUID().toString();

    @Test
    void reqRq002TrimsOuterUnicodeWhitespaceAndPreservesInternalText() {
        var draft = new RequestDraft("\u2003Broken light\u2003", "\nFirst line\n  second line\n", " Block A ", category, "NORMAL");
        assertEquals("Broken light", draft.title());
        assertEquals("First line\n  second line", draft.description());
        assertTrue(draft.errors().isEmpty());
    }

    @Test
    void reqRq002UnicodeFieldBoundariesAreCodePoints() {
        assertTrue(new RequestDraft("😀".repeat(5), "😀".repeat(10), "😀".repeat(2), category, "LOW").errors().isEmpty());
        assertTrue(new RequestDraft("😀".repeat(100), "😀".repeat(2000), "😀".repeat(120), category, "EMERGENCY").errors().isEmpty());
        assertEquals(3, new RequestDraft("😀".repeat(101), "😀".repeat(2001), "😀".repeat(121), category, "HIGH").errors().size());
        assertEquals(3, new RequestDraft("four", "short", "a", category, "NORMAL").errors().size());
    }

    @Test
    void reqRq002RejectsMissingMalformedAndUnsupportedValues() {
        assertEquals(5, new RequestDraft(null, null, null, null, null).errors().size());
        assertEquals(5, new RequestDraft("     ", "          ", "  ", "1-1-1-1-1", "CRITICAL").errors().size());
        assertTrue(new RequestDraft("Null\0title", "Valid description", "Block A", category, "NORMAL").errors().containsKey("title"));
    }
}
