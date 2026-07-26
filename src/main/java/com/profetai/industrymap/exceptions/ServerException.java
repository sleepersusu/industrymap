package com.profetai.industrymap.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 業務與 HTTP 錯誤統一表示方式，對齊 .claude/rules/code-style.md「Error Handling」規範。
 * catch 到例外只有三種合法出路：轉成本例外拋出、rethrow、或明確降級 + log；禁止吞掉不處理。
 */
@Getter
public class ServerException extends RuntimeException {

    private final HttpStatus httpStatus;

    public ServerException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ServerException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
