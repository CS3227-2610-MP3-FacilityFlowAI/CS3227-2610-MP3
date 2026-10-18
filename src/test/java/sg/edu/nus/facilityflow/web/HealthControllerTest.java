package sg.edu.nus.facilityflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;

class HealthControllerTest {

    private final JdbcOperations jdbc = mock(JdbcOperations.class);
    private final HealthController controller = new HealthController(jdbc);

    @Test
    void nfrOps001ReportsUpWithoutConnectionDetailsWhenDatabaseIsReady() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsEntry("status", "UP").hasSize(1);
    }

    @Test
    void nfrOps001ReportsOnlyDownWhenDatabaseIsUnavailable() {
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("synthetic provider detail"));

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getBody()).containsEntry("status", "DOWN").hasSize(1);
        assertThat(response.getBody().toString()).doesNotContain("synthetic provider detail");
    }
}
