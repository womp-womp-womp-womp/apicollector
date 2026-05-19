package com.example.apicollector;

import com.example.exception.ApiParsingException;
import com.example.exception.ErrorResponse;
import com.example.exception.ExternalApiException;
import com.example.exception.GlobalExceptionHandler;
import com.example.exception.IncrementalUpdateUnavailableException;
import com.example.exception.UpdateAlreadyRunningException;
import com.example.exception.UpdateInterruptedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/update");
    }

    @Test
    void mapsKnownUpdateAndApiFailuresToExpectedStatuses() {
        assertResponse(
                handler.handleExternalApi(new ExternalApiException("api down", 503, true), request),
                HttpStatus.SERVICE_UNAVAILABLE,
                "api down"
        );
        assertResponse(
                handler.handleApiParsing(new ApiParsingException("bad json"), request),
                HttpStatus.BAD_GATEWAY,
                "bad json"
        );
        assertResponse(
                handler.handleUpdateAlreadyRunning(new UpdateAlreadyRunningException("busy"), request),
                HttpStatus.CONFLICT,
                "busy"
        );
        assertResponse(
                handler.handleIncrementalUpdateUnavailable(new IncrementalUpdateUnavailableException("empty"), request),
                HttpStatus.CONFLICT,
                "empty"
        );
        assertResponse(
                handler.handleUpdateInterrupted(new UpdateInterruptedException("interrupted", new InterruptedException()), request),
                HttpStatus.SERVICE_UNAVAILABLE,
                "interrupted"
        );
    }

    @Test
    void mapsBadRequestsToUserFriendlyMessages() {
        assertResponse(
                handler.handleBadRequest(new IllegalArgumentException("bad limit"), request),
                HttpStatus.BAD_REQUEST,
                "bad limit"
        );
        assertResponse(
                handler.handleBadRequest(new MissingServletRequestParameterException("name", "String"), request),
                HttpStatus.BAD_REQUEST,
                "Required request parameter 'name' is missing"
        );
        assertResponse(
                handler.handleBadRequest(new HttpMessageNotReadableException("broken body", null), request),
                HttpStatus.BAD_REQUEST,
                "Request body is invalid or unreadable"
        );
    }

    @Test
    void mapsMethodDatabaseAndUnexpectedErrors() {
        assertResponse(
                handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("PATCH"), request),
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint"
        );
        assertResponse(
                handler.handleDatabase(new DataAccessResourceFailureException("db down"), request),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Database error. Please try again later"
        );
        assertResponse(
                handler.handleUnexpected(new RuntimeException("boom"), request),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error"
        );
    }

    private void assertResponse(
            ResponseEntity<ErrorResponse> response,
            HttpStatus status,
            String message
    ) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(message);
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().path()).isEqualTo("/update");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
