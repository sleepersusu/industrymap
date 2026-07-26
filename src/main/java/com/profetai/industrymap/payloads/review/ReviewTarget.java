package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.ReviewTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

    @Schema(description = "該類型底下的資料識別碼；與 naturalKey 擇一提供，兩者皆有時以本欄為準")
    private Long targetId;

    @Valid
    @Schema(description = "自然鍵定位；與 targetId 擇一提供")
    private ReviewTargetKey naturalKey;

    /**
     * 查詢回應刻意不曝露內部 id，若只收 id 就會有資料進得去、出不來的類型（design D1）；
     * 但兩種定位都沒給就無從得知要審哪一筆，必須擋在 API 邊界。
     */
    @AssertTrue(message = "必須提供目標識別碼或自然鍵其中一種定位方式")
    @Schema(hidden = true)
    public boolean isTargetLocatable() {
        return ReviewTargetLocation.isLocatable(targetId, naturalKey);
    }
}
