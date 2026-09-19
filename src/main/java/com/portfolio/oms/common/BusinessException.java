package com.portfolio.oms.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public BusinessException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static BusinessException missing(String type) {
    return new BusinessException(HttpStatus.NOT_FOUND, type + "_NOT_FOUND", type.toLowerCase() + " not found");
  }

  public static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }

  public static BusinessException invalid(String code, String message) {
    return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
  }
}
