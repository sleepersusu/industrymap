package com.profetai.industrymap.payloads;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link ServerResponse} 的 {@link ResponseEntity} 包裝。
 *
 * <p>controller 只要寫 {@code return ServerResponses.ok(data);}，
 * 不必每支端點重複 {@code ResponseEntity.status(...).body(ServerResponse.ok(...))} 這串樣板；
 * 狀態碼與包裝格式也因此集中在同一處，不會有某支端點包法不一致。</p>
 */
public final class ServerResponses {

    private ServerResponses() {
    }

    /** 200：一般查詢與更新成功 */
    public static <T> ResponseEntity<ServerResponse<T>> ok(T data) {
        return ResponseEntity.ok(ServerResponse.ok(data));
    }

    public static <T> ResponseEntity<ServerResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ServerResponse.ok(data, message));
    }

    /** 201：建立新資源或觸發新的同步任務 */
    public static <T> ResponseEntity<ServerResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ServerResponse.ok(data));
    }

    public static <T> ResponseEntity<ServerResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ServerResponse.ok(data, message));
    }
}
