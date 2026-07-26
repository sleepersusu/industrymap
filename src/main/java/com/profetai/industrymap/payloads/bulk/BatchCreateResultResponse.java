package com.profetai.industrymap.payloads.bulk;

import com.profetai.industrymap.enums.ReviewTargetType;
import com.profetai.industrymap.payloads.review.ReviewTargetKey;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批次建立中單筆項目的結果（design D3）。
 *
 * <p>成功項目一併帶回定位資訊（{@code targetType} + {@code naturalKey} + {@code targetId}），
 * 呼叫端可直接把整份回應轉成批次審核請求，不需再查詢任何端點——「建立與審核之間斷了一截」
 * 正是上一輪走查最痛的地方。</p>
 *
 * <p>失敗項目只帶原因與狀態碼，刻意不帶任何定位資訊，避免呼叫端誤以為建立成功。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批次建立的單筆結果")
public class BatchCreateResultResponse {

    @Schema(description = "對應請求中第幾筆，從 0 起算")
    private int index;

    @Schema(description = "該筆是否建立成功")
    private boolean success;

    @Schema(description = "失敗時的狀態碼，例：409 表示重複；成功時為空")
    private Integer statusCode;

    @Schema(description = "失敗原因；成功時為空")
    private String message;

    @Schema(description = "審核目標類型；失敗時為空")
    private ReviewTargetType targetType;

    @Schema(description = "建立出來的內部識別碼；失敗時為空")
    private Long targetId;

    @Schema(description = "可直接用於審核端點的自然鍵；失敗時為空")
    private ReviewTargetKey naturalKey;

    public static BatchCreateResultResponse success(int index, ReviewTargetType targetType, Long targetId,
                                                    ReviewTargetKey naturalKey) {
        return BatchCreateResultResponse.builder()
                .index(index)
                .success(true)
                .targetType(targetType)
                .targetId(targetId)
                .naturalKey(naturalKey)
                .build();
    }

    public static BatchCreateResultResponse failure(int index, int statusCode, String message) {
        return BatchCreateResultResponse.builder()
                .index(index)
                .success(false)
                .statusCode(statusCode)
                .message(message)
                .build();
    }
}
