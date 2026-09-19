package com.portfolio.oms.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {

  public record ErrorBody(
      Instant timestamp,
      int status,
      String error,
      String message,
      String path
  ) {}

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorBody> business(BusinessException e, HttpServletRequest r) {
    return response(e.status(), e.code(), e.getMessage(), r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class,
    org.springframework.web.bind.ServletRequestBindingException.class
  })
  ResponseEntity<ErrorBody> validation(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request fields or parameters are invalid", r);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ErrorBody> notFound(Exception e, HttpServletRequest r) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", r);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorBody> method(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method not supported", r);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorBody> media(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Content type not supported",
        r);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorBody> denied(Exception e, HttpServletRequest r) {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorBody> constraint(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.CONFLICT,
        "DATA_CONFLICT",
        "A unique value or relational constraint conflicts",
        r);
  }

  @ExceptionHandler({
    PessimisticLockingFailureException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ErrorBody> concurrent(Exception e, HttpServletRequest r) {
    return response(
        HttpStatus.CONFLICT,
        "CONCURRENT_CHANGE",
        "Concurrent operation; retry with the same idempotency key",
        r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest r) {
    LoggerFactory.getLogger(ApiErrors.class)
        .error(
            "Unhandled request failure type={} stack={}",
            e.getClass().getSimpleName(),
            java.util.Arrays.toString(e.getStackTrace()));
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", r);
  }

  private ResponseEntity<ErrorBody> response(
      HttpStatus s, String code, String message, HttpServletRequest r) {
    return ResponseEntity.status(s)
        .body(new ErrorBody(Instant.now(), s.value(), code, message, r.getRequestURI()));
  }
}
