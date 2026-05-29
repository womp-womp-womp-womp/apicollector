package com.example.apicollector;

import com.example.apiparser.InspectionApiClient;
import com.example.exception.ExternalApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionApiClientTest {

    private HttpServer server;
    private String baseUrl;
    private final Queue<Response> responses = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String lastQuery;
    private volatile String lastApiKey;
    private volatile String lastXApiKey;
    private volatile String lastAuthorization;
    private volatile String lastAccept;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inspections", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/inspections";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchJsonBuildsRequestWithPaginationAndApiKey() {
        responses.add(new Response(200, "{\"data\":[]}"));
        InspectionApiClient client = client();

        assertThat(client.fetchJson(2, 50, "secret")).isEqualTo("{\"data\":[]}");
        assertThat(lastQuery).isEqualTo("page=2&per_page=50&api-key=secret");
        assertThat(lastAccept).isEqualTo("application/json");
        assertThat(lastApiKey).isEqualTo("secret");
        assertThat(lastXApiKey).isEqualTo("secret");
        assertThat(lastAuthorization).isEqualTo("APIKEY secret");
        assertThat(requests).hasValue(1);
    }

    @Test
    void checkApiAvailabilityFetchesFirstPage() {
        responses.add(new Response(200, "{\"total\":0}"));

        client().checkApiAvailability("secret");

        assertThat(lastQuery).isEqualTo("page=1&per_page=1&api-key=secret");
    }

    @Test
    void fetchJsonRejectsInvalidSettingsAndParamsBeforeRequest() {
        InspectionApiClient client = client();

        assertThatThrownBy(() -> client.fetchJson(0, 10, "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be greater than 0");
        assertThatThrownBy(() -> client.fetchJson(1, 0, "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("perPage must be greater than 0");
        assertThatThrownBy(() -> new InspectionApiClient(RestClient.builder(), " ").fetchJson(1, 1, "secret"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("External API base URL is not configured");
        assertThatThrownBy(() -> new InspectionApiClient(RestClient.builder(), baseUrl).fetchJson(1, 1, "token"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("External API key is not configured. Provide an API key before running update");
    }

    @Test
    void fetchJsonMapsHttpErrorsWithStatusAndRetryability() {
        responses.add(new Response(400, "bad request"));

        assertThatThrownBy(() -> client().fetchJson(1, 1, "secret"))
                .isInstanceOfSatisfying(ExternalApiException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(400);
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).isEqualTo("External API returned HTTP 400: bad request");
                });
    }

    @Test
    void fetchJsonRetriesRetryableEmptyAndServerResponses() {
        responses.add(new Response(200, " "));
        responses.add(new Response(500, ""));
        responses.add(new Response(200, "{\"data\":[]}"));

        assertThat(client().fetchJson(1, 1, "secret")).isEqualTo("{\"data\":[]}");
        assertThat(requests).hasValue(3);
    }

    private InspectionApiClient client() {
        return new InspectionApiClient(RestClient.builder(), baseUrl);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        lastQuery = exchange.getRequestURI().getQuery();
        lastApiKey = exchange.getRequestHeaders().getFirst("API-Key");
        lastXApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        lastAccept = exchange.getRequestHeaders().getFirst("accept");
        Response response = responses.remove();
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record Response(int status, String body) {
    }
}
