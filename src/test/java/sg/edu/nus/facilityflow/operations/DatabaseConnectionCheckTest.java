package sg.edu.nus.facilityflow.operations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DatabaseConnectionCheckTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DatabaseConnectionCheck check = new DatabaseConnectionCheck(jdbc);

    @Test
    @DisplayName("TECHNICAL-SPEC: connection smoke check executes only a read")
    void verifiesConnectionWithoutWrites() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        check.run(null);
        verify(jdbc).queryForObject("SELECT 1", Integer.class);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    @DisplayName("NFR-SEC-002: provider connection details are excluded from smoke failure")
    void reportsSafeConnectionFailure() {
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("synthetic-private-provider-details"));
        var failure = assertThrows(IllegalStateException.class, () -> check.run(null));
        assertEquals("Database connection check failed. Check database settings, network and SSL certificate.",
                failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    @DisplayName("TECHNICAL-SPEC: unexpected database response fails the smoke check")
    void rejectsUnexpectedResponse() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> check.run(null));
    }
}
