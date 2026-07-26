package com.profetai.industrymap.payloads;

import lombok.Builder;
import lombok.Getter;

/**
 * 統一成功回應包裝，對齊 ais-backend 的 ServerResponse<T> 慣例（見 .claude/rules/api-design.md）。
 */
@Getter
@Builder
public class ServerResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;

    public static <T> ServerResponse<T> ok(T data) {
        return ServerResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ServerResponse<T> ok(T data, String message) {
        return ServerResponse.<T>builder().success(true).data(data).message(message).build();
    }
}
