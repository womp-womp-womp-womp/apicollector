package com.example.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(
            ExternalApiException e,
            HttpServletRequest request
    ) {
        log.warn("External API error. path={}, retryable={}, statusCode={}, message={}",
                request.getRequestURI(), e.isRetryable(), e.getStatusCode(), e.getMessage(), e);

        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler(ApiParsingException.class)
    public ResponseEntity<ErrorResponse> handleApiParsing(
            ApiParsingException e,
            HttpServletRequest request
    ) {
        log.warn("External API response parsing error. path={}, message={}",
                request.getRequestURI(), e.getMessage(), e);

        return build(HttpStatus.BAD_GATEWAY, e.getMessage(), request);
    }

    @ExceptionHandler(UpdateAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleUpdateAlreadyRunning(
            UpdateAlreadyRunningException e,
            HttpServletRequest request
    ) {
        log.info("Rejected update request because another update is already running. path={}", request.getRequestURI());
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }


    @ExceptionHandler(IncrementalUpdateUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleIncrementalUpdateUnavailable(
            IncrementalUpdateUnavailableException e,
            HttpServletRequest request
    ) {
        log.info("Rejected incremental update request because prerequisite is missing. path={}, message={}",
                request.getRequestURI(), e.getMessage());
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(UpdateInterruptedException.class)
    public ResponseEntity<ErrorResponse> handleUpdateInterrupted(
            UpdateInterruptedException e,
            HttpServletRequest request
    ) {
        log.warn("Update operation was interrupted. path={}, message={}", request.getRequestURI(), e.getMessage(), e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception e,
            HttpServletRequest request
    ) {
        log.warn("Bad request. path={}, message={}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, userMessageForBadRequest(e), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request
    ) {
        log.warn("Method not allowed. path={}, method={}", request.getRequestURI(), e.getMethod());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException e,
            HttpServletRequest request
    ) {
        log.warn("Resource not found. path={}", request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Endpoint was not found", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabase(
            DataAccessException e,
            HttpServletRequest request
    ) {
        log.error("Database error. path={}, message={}", request.getRequestURI(), e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Database error. Please try again later", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e,
            HttpServletRequest request
    ) {
        log.error("Unexpected error. path={}, message={}", request.getRequestURI(), e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(
                        message,
                        status.value(),
                        LocalDateTime.now(),
                        request.getRequestURI()
                ));
    }

    private String userMessageForBadRequest(Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            return "Invalid value for request parameter '" + mismatch.getName() + "'";
        }

        if (e instanceof MissingServletRequestParameterException missing) {
            return "Required request parameter '" + missing.getParameterName() + "' is missing";
        }

        if (e instanceof HttpMessageNotReadableException) {
            return "Request body is invalid or unreadable";
        }

        return e.getMessage() == null ? "Invalid request" : e.getMessage();
    }
}
