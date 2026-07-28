package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.ShareMetric;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.MarketShare;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市佔率一筆資料。來源欄位一定要回傳——同一組維度可能有兩個來源給出互相矛盾的數字，
 * 前端必須把來源一併呈現，讓使用者自己判斷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "市佔率")
public class MarketShareResponse {

    @Schema(description = "公司顯示名稱")
    private String companyName;

    @Schema(description = "公司的對外識別：主要代號的交易所限定形式 <類型>:<代號值>，"
            + "無識別碼的公司則為正規化名稱", example = "TWSE:2330")
    private String companyReference;

    @Schema(description = "市佔百分比", example = "70.5")
    private BigDecimal sharePercent;

    @Schema(description = "期間單位")
    private PeriodType periodType;

    @Schema(description = "期間值", example = "2024")
    private String periodValue;

    @Schema(description = "地區", example = "全球")
    private String region;

    @Schema(description = "口徑")
    private ShareMetric metric;

    @Schema(description = "來源類型")
    private SourceType sourceType;

    @Schema(description = "來源明細")
    private String sourceDetail;

    @Schema(description = "審核狀態")
    private ReviewStatus reviewStatus;

    /**
     * @param companyReference 公司對外識別，由 {@code CompanyReferences} 統一組出（design D4）
     */
    public static MarketShareResponse from(MarketShare marketShare, String companyReference) {
        return MarketShareResponse.builder()
                .companyName(marketShare.getCompany().getDisplayName())
                .companyReference(companyReference)
                .sharePercent(marketShare.getSharePercent())
                .periodType(marketShare.getPeriodType())
                .periodValue(marketShare.getPeriodValue())
                .region(marketShare.getRegion())
                .metric(marketShare.getMetric())
                .sourceType(marketShare.getSourceType())
                .sourceDetail(marketShare.getSourceDetail())
                .reviewStatus(marketShare.getReviewStatus())
                .build();
    }
}
