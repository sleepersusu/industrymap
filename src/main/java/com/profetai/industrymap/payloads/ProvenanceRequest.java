package com.profetai.industrymap.payloads;

import com.profetai.industrymap.enums.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 所有內容資料寫入時共用的來源欄位（design D8）。
 *
 * <p>能以 annotation 表達的規則（必填、數值範圍、AI 生成必帶信心度）都寫在這裡，
 * 由 {@code @Valid} 在 API 邊界擋下；service 層另有同義的驗證，
 * 讓非 HTTP 進入點（未來的批次匯入）也守得住同一組規則。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "資料來源與信心度")
public class ProvenanceRequest {

    @NotNull(message = "來源類型為必填")
    @Schema(description = "來源類型", requiredMode = Schema.RequiredMode.REQUIRED)
    private SourceType sourceType;

    @Schema(description = "來源明細：模型識別、報告名稱或 URL")
    private String sourceDetail;

    @DecimalMin(value = "0.0", message = "信心度不得小於 0")
    @DecimalMax(value = "1.0", message = "信心度不得大於 1")
    @Schema(description = "信心度 0–1，來源類型為 AI_GENERATED 時必填")
    private BigDecimal confidence;

    /** AI 生成的資料沒有信心度就無從判斷可信程度，等同沒有來源 */
    @AssertTrue(message = "來源類型為 AI_GENERATED 時必須提供信心度")
    @Schema(hidden = true)
    public boolean isConfidenceProvidedForAiSource() {
        return sourceType != SourceType.AI_GENERATED || confidence != null;
    }
}
