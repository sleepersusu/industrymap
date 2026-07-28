package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市佔率寫入請求。期間、地區、口徑缺一數字就沒有意義（design D7），
 * 因此三者都是必填，能用 annotation 表達的部分直接由 {@code @Valid} 擋在 API 邊界。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "寫入市佔率的請求")
public class CreateMarketShareRequest {

    @NotBlank(message = "公司代號為必填")
    @Schema(description = "公司對外識別，建議用交易所限定形式 <類型>:<代號值>；未上市公司用正規化名稱。"
            + "與公司回應的 reference 一致。裸代號仍可用，但同代號值跨交易所撞號時回 409",
            example = "TWSE:5306", requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyCode;

    @NotNull(message = "零件為必填")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @NotNull(message = "期間單位為必填")
    @Schema(description = "期間單位：年或季", requiredMode = Schema.RequiredMode.REQUIRED)
    private PeriodType periodType;

    @NotBlank(message = "期間為必填")
    @Schema(description = "期間值，例：2024 或 2024Q3", example = "2024", requiredMode = Schema.RequiredMode.REQUIRED)
    private String periodValue;

    @NotBlank(message = "地區為必填")
    @Schema(description = "地區", example = "全球", requiredMode = Schema.RequiredMode.REQUIRED)
    private String region;

    @NotNull(message = "口徑為必填")
    @Schema(description = "口徑：營收或出貨量", requiredMode = Schema.RequiredMode.REQUIRED)
    private ShareMetric metric;

    @NotNull(message = "市佔百分比為必填")
    @DecimalMin(value = "0.0", message = "市佔百分比不得小於 0")
    @DecimalMax(value = "100.0", message = "市佔百分比不得大於 100")
    @Schema(description = "市佔百分比 0–100", example = "70.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal sharePercent;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;
}
