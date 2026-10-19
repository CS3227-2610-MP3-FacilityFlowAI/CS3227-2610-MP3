package sg.edu.nus.facilityflow.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import sg.edu.nus.facilityflow.requestcore.RequestDraft;
import sg.edu.nus.facilityflow.requester.RequesterRequests;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequesterRequestControllerTest {
    @Test
    void reqRq011LateAuthorizationDenialRetainsOnlySubmittedInputAndSafeGuidance() {
        var requests = mock(RequesterRequests.class);
        var draft = new RequestDraft("Broken light", "The lamp is flickering", "Block A", "invalid", "NORMAL");
        when(requests.create(draft, "command")).thenThrow(new AccessDeniedException("private failure"));
        var model = new ExtendedModelMap();
        model.addAttribute("form", draft);
        var response = new MockHttpServletResponse();
        assertEquals("requester/create", new RequesterRequestController(requests).create(draft, "command", model, response));
        assertEquals(403, response.getStatus());
        assertEquals("command", model.get("submissionId"));
        assertEquals(draft, model.get("form"));
        assertFalse(model.get("message").toString().contains("private failure"));
        verify(requests, never()).categories();
    }

    @Test
    void reqRq011AccessChangeDuringRecoveryDoesNotLoseInput() {
        var requests = mock(RequesterRequests.class);
        var draft = new RequestDraft("Broken light", "The lamp is flickering", "Block A", "invalid", "NORMAL");
        when(requests.create(draft, "command")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database details"));
        when(requests.categories()).thenThrow(new AccessDeniedException("private account details"));
        var model = new ExtendedModelMap();
        model.addAttribute("form", draft);
        var response = new MockHttpServletResponse();
        assertEquals("requester/create", new RequesterRequestController(requests).create(draft, "command", model, response));
        assertEquals(403, response.getStatus());
        assertEquals(draft, model.get("form"));
        assertFalse(model.get("message").toString().contains("private"));
    }
}
