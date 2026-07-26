package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 單筆審核請求（design D1）。
 *
 * <p>必填與長度規則一律以 annotation 表達，由 {@code @Valid} 擋在 API 邊界；
 * service 層另有同義驗證（{@code ReviewService}），讓未來的批次匯入等非 HTTP 進入點
 * 守得住同一組規則。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "單筆審核請求")
public class ReviewRequest {

    @NotNull(message = "目標類型為必填")
    @Schema(description = "目標類型：ITEM / ITEM_ALIAS / ITEM_COMPOSITION / COMPANY / COMPANY_ALIAS "
            + "/ COMPANY_IDENTIFIER / COMPANY_ITEM_ROLE / MARKET_SHARE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewTargetType targetType;

    @NotNull(message = "目標識別碼為必填")
    @Schema(description = "該類型底下的資料識別碼", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;

    @NotNull(message = "目標審核狀態為必填")
    @Schema(description = "目標審核狀態：DRAFT / VERIFIED / REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewStatus targetStatus;

    @Size(max = 128, message = "審核者長度不得超過 128")
    @Schema(description = "審核者；目標狀態為 VERIFIED 或 REJECTED 時必填")
    private String reviewer;

    /** 退回草稿代表「這筆還沒被審過」，不需審核者；蓋章則一定要留下是誰蓋的 */
    @AssertTrue(message = "標記為已驗證或已駁回時必須提供審核者")
    @Schema(hidden = true)
    public boolean isReviewerProvidedWhenRequired() {
        return ReviewerRequirement.isSatisfied(targetStatus, reviewer);
    }
}
