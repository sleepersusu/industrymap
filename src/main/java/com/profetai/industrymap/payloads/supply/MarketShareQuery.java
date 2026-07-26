package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ShareMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市佔率排名查詢條件。期間、地區、口徑三個維度缺一，排名就沒有意義（design D7），
 * 因此三者都是必填，由 {@code @Valid} 在 API 邊界擋下。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "市佔率排名查詢條件")
public class MarketShareQuery {

    @NotNull(message = "期間單位為必填")
    @Schema(description = "期間單位：年或季", requiredMode = Schema.RequiredMode.REQUIRED)
    private PeriodType periodType;

    @NotBlank(message = "期間為必填")
    @Schema(description = "期間值", example = "2024", requiredMode = Schema.RequiredMode.REQUIRED)
    private String periodValue;

    @NotBlank(message = "地區為必填")
    @Schema(description = "地區", example = "全球", requiredMode = Schema.RequiredMode.REQUIRED)
    private String region;

    @NotNull(message = "口徑為必填")
    @Schema(description = "口徑：營收或出貨量", requiredMode = Schema.RequiredMode.REQUIRED)
    private ShareMetric metric;

    @Builder.Default
    @Schema(description = "是否納入草稿資料；已駁回一律不外露", example = "false")
    private boolean includeDrafts = false;
}
