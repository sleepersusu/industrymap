package com.profetai.industrymap.advice;

import com.profetai.industrymap.exceptions.ServerException;
import com.profetai.industrymap.payloads.ServerResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServerException.class)
    public ResponseEntity<ServerResponse<Void>> handleServerException(ServerException ex) {
        log.warn("ServerException: {} ({})", ex.getMessage(), ex.getHttpStatus());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ServerResponse.<Void>builder().success(false).message(ex.getMessage()).build());
    }

    /** request body 的 {@code @Valid} 驗證失敗：把欄位訊息攤平成一句，前端不必再解析結構 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ServerResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("；"));
        log.warn("請求驗證失敗: {}", message);
        return badRequest(message.isBlank() ? "請求欄位驗證失敗" : message);
    }

    /** query / path 參數的 {@code @Validated} 驗證失敗 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ServerResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("參數驗證失敗: {}", ex.getMessage());
        return badRequest(ex.getMessage());
    }

    /** 列舉值拼錯、數字欄位塞了文字等型別轉換失敗，語意上是請求錯誤而非伺服器錯誤 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ServerResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("參數型別錯誤: name={} value={}", ex.getName(), ex.getValue());
        return badRequest("參數格式不正確：" + ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ServerResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("缺少必要參數: {}", ex.getParameterName());
        return badRequest("缺少必要參數：" + ex.getParameterName());
    }

    private String describeFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ServerResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ServerResponse.<Void>builder().success(false).message(message).build());
    }
}
