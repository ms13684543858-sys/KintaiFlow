package com.example.kintaiflow.exception;

import org.springframework.http.HttpStatus;

/** 業務エラー。メッセージ一覧（KF-BD-006）のコードと文言を持つ。 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
