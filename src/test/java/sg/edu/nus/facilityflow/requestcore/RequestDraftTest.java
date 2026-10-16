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

    @Test
    void reqRq007008ReasonAndUpdateUnicodeBoundariesAndTrimming() {
        assertEquals("😀".repeat(5), RequestDraft.validatedText("reason", "Reason", "\u2003" + "😀".repeat(5) + " ", 5, 500));
        assertEquals(500, RequestDraft.validatedText("reason", "Reason", "😀".repeat(500), 5, 500).codePointCount(0, 1000));
        assertEquals("x", RequestDraft.validatedText("note", "Update", " x ", 1, 1000));
        assertEquals("😀".repeat(1000), RequestDraft.validatedText("note", "Update", "😀".repeat(1000), 1, 1000));
        for (String reason : new String[]{"four", "😀".repeat(501), " \n ", "reason\0"}) {
            assertThrows(RequestValidationException.class, () -> RequestDraft.validatedText("reason", "Reason", reason, 5, 500));
        }
        for (String note : new String[]{"", " \n ", "😀".repeat(1001)}) {
            assertThrows(RequestValidationException.class, () -> RequestDraft.validatedText("note", "Update", note, 1, 1000));
        }
    }
}
