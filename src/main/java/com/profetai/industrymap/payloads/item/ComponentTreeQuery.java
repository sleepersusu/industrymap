package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.Necessity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 組成樹查詢條件。以 payload 承載而非逐個 query 參數，
 * 驗證規則（深度上限）也就跟著條件本身走，不散落在 controller 簽章上。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "組成樹查詢條件")
public class ComponentTreeQuery {

    @Min(value = 1, message = "展開層數至少為 1")
    @Max(value = 5, message = "展開層數上限為 5")
    @Builder.Default
    @Schema(description = "展開層數 1–5；上限存在是為了避免一次拉出整張圖", example = "1")
    private int depth = 1;

    @Schema(description = "只取指定必要性的組成；不填表示全取")
    private Necessity necessity;

    @Builder.Default
    @Schema(description = "是否納入草稿資料；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
