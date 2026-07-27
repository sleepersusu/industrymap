package com.profetai.industrymap.payloads.item;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 終端成品列表的查詢條件。以 payload 承載而非逐個 query 參數，
 * 分頁邊界的驗證規則也就跟著條件本身走，不散落在 controller 簽章上。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "終端成品列表查詢條件")
public class EndProductQuery {

    @Min(value = 0, message = "頁碼不可為負數")
    @Builder.Default
    @Schema(description = "頁碼，自 0 起算", example = "0")
    private int page = 0;

    @Min(value = 1, message = "每頁筆數至少為 1")
    @Max(value = 100, message = "每頁筆數上限為 100")
    @Builder.Default
    @Schema(description = "每頁筆數 1–100；上限存在是為了避免一次拉出整張清單", example = "20")
    private int size = 20;

    @Schema(description = "名稱關鍵字，正規化後做包含比對；不填表示不過濾", example = "主機板")
    private String name;

    @Builder.Default
    @Schema(description = "是否納入草稿資料；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
