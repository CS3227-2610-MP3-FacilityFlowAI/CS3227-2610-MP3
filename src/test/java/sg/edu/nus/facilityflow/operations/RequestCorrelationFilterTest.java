package sg.edu.nus.facilityflow.operations;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.UUID;

class RequestCorrelationFilterTest {
    @Test @DisplayName("NFR-OPS-002: server-generated correlation ignores caller values and restores the previous context")
    void ignoresCallerAndCleansUpAfterFailure() {
        var request = new MockHttpServletRequest("POST", "/requester/requests/assistant");
        request.addHeader("X-Correlation-ID", "untrusted-secret-canary");
        request.addParameter("roughText", "private-description-canary");
        var response = new MockHttpServletResponse();
        MDC.put(RequestCorrelationFilter.KEY, "previous");
        try {
            assertThrows(IllegalStateException.class, () -> new RequestCorrelationFilter().doFilter(request, response, (req, res) -> {
                String id = MDC.get(RequestCorrelationFilter.KEY);
                assertDoesNotThrow(() -> UUID.fromString(id));
                assertNotEquals("untrusted-secret-canary", id);
                assertEquals(id, response.getHeader("X-Correlation-ID"));
                throw new IllegalStateException("synthetic");
            }));
            assertEquals("previous", MDC.get(RequestCorrelationFilter.KEY));
        } finally { MDC.clear(); }
    }

    @Test @DisplayName("NFR-OPS-002: async redispatch retains its server correlation without leaking thread context")
    void retainsCorrelationOnAsyncRedispatch() throws Exception {
        var request = new MockHttpServletRequest("POST", "/technician/requests/FF-000001/work-plan");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        var filter = new RequestCorrelationFilter();
        filter.doFilter(request, response, (req, res) -> request.startAsync());
        String original = response.getHeader("X-Correlation-ID");
        assertNull(MDC.get(RequestCorrelationFilter.KEY));
        request.setDispatcherType(DispatcherType.ASYNC);
        request.setAsyncStarted(false);
        filter.doFilter(request, response, (req, res) -> assertEquals(original, MDC.get(RequestCorrelationFilter.KEY)));
        assertEquals(original, response.getHeader("X-Correlation-ID"));
        assertNull(MDC.get(RequestCorrelationFilter.KEY));
    }
}
