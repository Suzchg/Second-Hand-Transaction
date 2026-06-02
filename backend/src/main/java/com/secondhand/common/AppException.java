package com.secondhand.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。
 * 抛出后由 {@link GlobalExceptionHandler} 统一转换为 ApiResponse。
 */
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public AppException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
