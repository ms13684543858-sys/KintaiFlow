package com.example.kintaiflow.exception;

import com.example.kintaiflow.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** 例外を共通のエラー形式（code / message / fieldErrors）に変換する。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    /** @Valid による入力チェックエラー（E-002）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        String message = fields.isEmpty() ? "入力内容が正しくありません。" : fields.get(0).message();
        return ResponseEntity.badRequest().body(new ErrorResponse("E-002", message, fields));
    }

    /** JSON の形式が不正、またはリクエスト本文が無い場合。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("E-002", "入力内容が正しくありません。"));
    }

    /** パス変数・クエリの型変換エラー（例：id に文字列）、必須クエリの欠落。 */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("E-002", "入力内容が正しくありません。"));
    }

    /** @PreAuthorize による権限不足（E-010）。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("E-010", "この操作を行う権限がありません。"));
    }

    /** 存在しないURL。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("E-015", "対象のデータが見つかりません。"));
    }

    /** 想定外のエラー（E-012）。詳細はログにだけ出し、画面には出さない。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of("E-012", "システムエラーが発生しました。しばらくしてから再度お試しください。"));
    }
}
