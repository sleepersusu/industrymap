package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批次審核請求（design D2）。
 *
 * <p>目標狀態與審核者由整批共用——批次的實際用途是「建完一台腳踏車後一次審掉全部」，
 * 逐筆指定狀態只會讓請求更囉嗦。空批次由 {@code @NotEmpty} 在 API 邊界擋下，不進 service。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批次審核請求")
public class BatchReviewRequest {

    @Valid
    @NotEmpty(message = "審核目標不得為空")
    @Schema(description = "審核目標清單，可跨不同資料類型", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReviewTarget> targets;

    @NotNull(message = "目標審核狀態為必填")
    @Schema(description = "整批共用的目標審核狀態", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewStatus targetStatus;

    @Size(max = 128, message = "審核者長度不得超過 128")
    @Schema(description = "整批共用的審核者；目標狀態為 VERIFIED 或 REJECTED 時必填")
    private String reviewer;

    @AssertTrue(message = "標記為已驗證或已駁回時必須提供審核者")
    @Schema(hidden = true)
    public boolean isReviewerProvidedWhenRequired() {
        return ReviewerRequirement.isSatisfied(targetStatus, reviewer);
    }
}
