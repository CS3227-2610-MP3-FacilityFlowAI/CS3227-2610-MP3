package sg.edu.nus.facilityflow.ai.provider;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Package-private transport seam; tests can provide responses without a network. */
interface ProviderHttpTransport {

    CompletableFuture<HttpResponse<InputStream>> send(HttpRequest request);
}

/** JDK transport with redirects disabled and the platform's normal TLS checks. */
final class JdkProviderHttpTransport implements ProviderHttpTransport {
    private final java.net.http.HttpClient client;

    JdkProviderHttpTransport() {
        this(java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build());
    }

    JdkProviderHttpTransport(java.net.http.HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletableFuture<HttpResponse<InputStream>> send(HttpRequest request) {
        Objects.requireNonNull(request, "request");
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
    }
}
