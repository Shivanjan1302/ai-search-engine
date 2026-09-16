package com.dronzer.aisearch.exception;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.validation.ConstraintViolationException;

import com.dronzer.aisearch.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected server error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Request validation failed");

        return errorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Request body must contain valid JSON",
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {

        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Request validation failed");
        return errorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInput(
            IllegalArgumentException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            EmailAlreadyRegisteredException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Required request parameter '" + exception.getParameterName() + "' is missing",
                request);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestBindingFailure(
            ServletRequestBindingException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Required request information is missing",
                request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Required request part '" + exception.getRequestPartName() + "' is missing",
                request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedMultipartRequest(
            MultipartException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Request must contain valid multipart data",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameterType(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Request parameter '" + exception.getName() + "' has an invalid value",
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint",
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported for this endpoint",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.NOT_FOUND, "Requested resource was not found", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleOversizedUpload(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size", request);
    }

    @ExceptionHandler(UploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationUploadLimit(
            UploadSizeExceededException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedDocumentException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedDocument(
            UnsupportedDocumentException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), request);
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentProcessing(
            DocumentProcessingException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request);
    }

    @ExceptionHandler(GeminiUpstreamException.class)
    public ResponseEntity<ApiErrorResponse> handleGeminiFailure(
            GeminiUpstreamException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage(),
                request,
                GeminiUpstreamException.CODE);
    }

    @ExceptionHandler(WebSearchUpstreamException.class)
    public ResponseEntity<ApiErrorResponse> handleWebSearchFailure(
            WebSearchUpstreamException exception,
            HttpServletRequest request) {

        return errorResponse(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage(),
                request,
                WebSearchUpstreamException.CODE);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {

        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        log.error("Unhandled exception while processing {} {}",
                request.getMethod(), request.getRequestURI(), exception);

        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, request);
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        return errorResponse(status, message, request, null);
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            String code) {

        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                code));
    }
}