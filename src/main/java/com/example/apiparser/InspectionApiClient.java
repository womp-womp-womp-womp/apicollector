package com.example.apiparser;

import com.example.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class InspectionApiClient {

    private static final Logger log = LoggerFactory.getLogger(InspectionApiClient.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public InspectionApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.api.base-url}") String baseUrl,
            @Value("${app.api.key}") String apiKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    private String buildInspectionsUrl(long page, long perPage) {
        return UriComponentsBuilder.fromUri(URI.create(baseUrl))
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .toUriString();
    }

    public void checkApiAvailability() {
        fetchJson(1, 1);
    }

    public String fetchJson(long page, long perPage) {
        validateRequestParams(page, perPage);
        validateApiSettings();

        ExternalApiException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return fetchJsonOnce(page, perPage);
            } catch (ExternalApiException e) {
                lastException = e;

                if (!e.isRetryable() || attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                log.warn(
                        "External API request failed. Retrying. page={}, perPage={}, attempt={}/{}, message={}",
                        page,
                        perPage,
                        attempt,
                        MAX_ATTEMPTS,
                        e.getMessage()
                );

                sleepBeforeRetry();
            }
        }

        throw lastException == null
                ? new ExternalApiException("External API request failed")
                : lastException;
    }

    private String fetchJsonOnce(long page, long perPage) {
        String url = buildInspectionsUrl(page, perPage);

        try {
            String body = restClient
                    .get()
                    .uri(url)
                    .header("accept", "application/json")
                    .header("API-Key", apiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        int statusCode = response.getStatusCode().value();
                        String errorBody = readErrorBody(response.getBody());
                        boolean retryable = isRetryableStatus(statusCode);

                        throw new ExternalApiException(
                                "External API returned HTTP " + statusCode + ": " + errorBody,
                                statusCode,
                                retryable
                        );
                    })
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new ExternalApiException(
                        "External API returned an empty response",
                        null,
                        null,
                        true
                );
            }

            return body;
        } catch (ExternalApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ExternalApiException(
                    "Cannot connect to external API: network unavailable, timeout, or API is not responding",
                    e,
                    true
            );
        } catch (RestClientException e) {
            throw new ExternalApiException(
                    "Failed to execute external API request",
                    e,
                    true
            );
        }
    }

    private void validateRequestParams(long page, long perPage) {
        if (page <= 0) {
            throw new IllegalArgumentException("page must be greater than 0");
        }

        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be greater than 0");
        }
    }

    private void validateApiSettings() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ExternalApiException("External API base URL is not configured", null, null, false);
        }

        if (apiKey == null || apiKey.isBlank() || "token".equals(apiKey)) {
            throw new ExternalApiException(
                    "External API key is not configured. Set APICOLLECTOR_API_KEY before running /updateAll",
                    null,
                    null,
                    false
            );
        }
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private String readErrorBody(java.io.InputStream body) {
        if (body == null) {
            return "empty error body";
        }

        try {
            String errorBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);

            if (errorBody.isBlank()) {
                return "empty error body";
            }

            if (errorBody.length() > MAX_ERROR_BODY_LENGTH) {
                return errorBody.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
            }

            return errorBody;
        } catch (IOException e) {
            return "failed to read error body";
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("Operation was interrupted while waiting before retry", e, true);
        }
    }
}
