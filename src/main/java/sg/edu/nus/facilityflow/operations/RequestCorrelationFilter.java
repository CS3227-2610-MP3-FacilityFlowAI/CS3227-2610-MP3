package sg.edu.nus.facilityflow.operations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** NFR-OPS-002: safe request metadata with correlation retained on async redispatch. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String KEY = "correlation_id";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".id";

    public static UUID currentId() {
        try { return UUID.fromString(MDC.get(KEY)); }
        catch (RuntimeException absent) { return UUID.randomUUID(); }
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID id = (UUID) request.getAttribute(ATTRIBUTE);
        if (id == null) { id = UUID.randomUUID(); request.setAttribute(ATTRIBUTE, id); }
        String previous = MDC.get(KEY);
        MDC.put(KEY, id.toString());
        response.setHeader("X-Correlation-ID", id.toString());
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            if (!request.isAsyncStarted()) {
                LOGGER.atInfo().addKeyValue("event", completed ? "HTTP_REQUEST_FINISHED" : "HTTP_REQUEST_FAILED")
                        .addKeyValue("correlation_id", id).addKeyValue("http_status", completed ? response.getStatus() : null)
                        .log("Application request dispatch ended");
            }
            if (previous == null) MDC.remove(KEY);
            else MDC.put(KEY, previous);
        }
    }
}
