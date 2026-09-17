package com.erpschool.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends ApiException {

    public BusinessException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    public BusinessException(String message) {
        super(HttpStatus.BAD_REQUEST, "BUSINESS_RULE", message);
    }
}
