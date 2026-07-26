package com.profetai.industrymap.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 只需要審核範圍的查詢條件。獨立成一個 payload，
 * 讓「預設只回已驗證、草稿需明確指定、已駁回不外露」這條規則在所有查詢端點長得一樣。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "審核範圍查詢條件")
public class ReviewScopeQuery {

    @Builder.Default
    @Schema(description = "是否納入草稿資料；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
