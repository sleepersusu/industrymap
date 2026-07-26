package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.ReviewTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 單一審核目標的定位資訊。批次請求中每筆只需指出「哪一類的哪一筆」，
 * 目標狀態與審核者由整批共用（見 {@link BatchReviewRequest}）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "審核目標")
public class ReviewTarget {

    @NotNull(message = "目標類型為必填")
    @Schema(description = "目標類型：ITEM / ITEM_ALIAS / ITEM_COMPOSITION / COMPANY / COMPANY_ALIAS "
            + "/ COMPANY_IDENTIFIER / COMPANY_ITEM_ROLE / MARKET_SHARE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewTargetType targetType;

    @NotNull(message = "目標識別碼為必填")
    @Schema(description = "該類型底下的資料識別碼", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;
}
