package com.example.kintaiflow.dto;

import java.util.List;

/** 共通のエラー応答（KF-DD-002 共通仕様）。 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }
}
