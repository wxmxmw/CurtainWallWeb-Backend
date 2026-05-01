package com.company.curtainwall.common;

import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        e.printStackTrace();
        return ApiResponse.error("Internal Server Error: " + e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e) {
        List<?> errors = e.getAllErrors();
        String message = null;
        if (errors != null && !errors.isEmpty()) {
            Object first = errors.get(0);
            if (first instanceof org.springframework.validation.ObjectError objectError) {
                message = objectError.getDefaultMessage();
            }
        }
        return ApiResponse.error(message);
    }
    
    // Add more specific exception handlers here (e.g., custom BusinessException)
}
