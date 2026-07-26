package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.model.ProvenanceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 單筆審核結果。批次中個別失敗不影響其他項目（design D2），
 * 因此成功與否、失敗原因都掛在每一筆上，而不是整個請求一個狀態碼。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "單筆審核結果")
public class ReviewResultResponse {

    @Schema(description = "目標類型")
    private ReviewTargetType targetType;

    @Schema(description = "目標識別碼")
    private Long targetId;

    @Schema(description = "該筆是否審核成功")
    private boolean success;

    @Schema(description = "更新後的審核狀態；失敗時為空")
    private ReviewStatus reviewStatus;

    @Schema(description = "審核者；退回草稿時為空")
    private String reviewedBy;

    @Schema(description = "審核時間；退回草稿時為空")
    private Instant reviewedAt;

    @Schema(description = "失敗原因；成功時為空")
    private String message;

    public static ReviewResultResponse from(ReviewTargetType targetType, Long targetId, ProvenanceEntity entity) {
        return ReviewResultResponse.builder()
                .targetType(targetType)
                .targetId(targetId)
                .success(true)
                .reviewStatus(entity.getReviewStatus())
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .build();
    }

    public static ReviewResultResponse failure(ReviewTargetType targetType, Long targetId, String message) {
        return ReviewResultResponse.builder()
                .targetType(targetType)
                .targetId(targetId)
                .success(false)
                .message(message)
                .build();
    }
}
