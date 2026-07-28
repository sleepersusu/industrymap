package com.profetai.industrymap.payloads.review;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.enums.PeriodType;
import com.profetai.industrymap.enums.ShareMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 審核目標的自然鍵（design D1）。
 *
 * <p>各資料類型的自然鍵即其資料庫唯一鍵，欄位在此攤平成一層：AI 生成流程產出的是名稱與代號，
 * 攤平後它可以直接把產出填進來，不必先判斷該包成哪一種巢狀結構。哪些欄位對哪個類型必填，
 * 由 {@code NaturalKeyResolver} 依 {@code targetType} 檢查並在缺漏時回 400（design D2）。</p>
 *
 * <table>
 *   <caption>各類型的自然鍵組合</caption>
 *   <tr><th>類型</th><th>欄位</th></tr>
 *   <tr><td>ITEM</td><td>name</td></tr>
 *   <tr><td>ITEM_ALIAS</td><td>name（別名）</td></tr>
 *   <tr><td>ITEM_COMPOSITION</td><td>parentItemId + childItemId</td></tr>
 *   <tr><td>COMPANY</td><td>companyCode</td></tr>
 *   <tr><td>COMPANY_ALIAS</td><td>name（別名）</td></tr>
 *   <tr><td>COMPANY_IDENTIFIER</td><td>identifierType + identifierValue</td></tr>
 *   <tr><td>COMPANY_ITEM_ROLE</td><td>companyCode + itemId + companyRole</td></tr>
 *   <tr><td>MARKET_SHARE</td><td>companyCode + itemId + periodType + periodValue + region + metric + sourceDetail</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "審核目標的自然鍵；依 targetType 填入對應欄位")
public class ReviewTargetKey {

    @Schema(description = "名稱或別名，系統會正規化後比對。用於 ITEM、ITEM_ALIAS、COMPANY_ALIAS", example = "變速器")
    private String name;

    @Schema(description = "公司對外識別（主要代號的交易所限定形式 <類型>:<代號值>，未上市公司為正規化名稱）。"
            + "用於 COMPANY、COMPANY_ITEM_ROLE、MARKET_SHARE", example = "TWSE:2330")
    private String companyCode;

    @Schema(description = "上層節點 id。用於 ITEM_COMPOSITION")
    private Long parentItemId;

    @Schema(description = "下層零件 id。用於 ITEM_COMPOSITION")
    private Long childItemId;

    @Schema(description = "零件 id。用於 COMPANY_ITEM_ROLE、MARKET_SHARE")
    private Long itemId;

    @Schema(description = "識別碼類型。用於 COMPANY_IDENTIFIER")
    private IdentifierType identifierType;

    @Schema(description = "識別碼值。用於 COMPANY_IDENTIFIER", example = "2330")
    private String identifierValue;

    @Schema(description = "供應角色。用於 COMPANY_ITEM_ROLE")
    private CompanyRole companyRole;

    @Schema(description = "期間單位。用於 MARKET_SHARE")
    private PeriodType periodType;

    @Schema(description = "期間值。用於 MARKET_SHARE", example = "2024")
    private String periodValue;

    @Schema(description = "地區。用於 MARKET_SHARE", example = "全球")
    private String region;

    @Schema(description = "口徑。用於 MARKET_SHARE")
    private ShareMetric metric;

    @Schema(description = "來源明細。用於 MARKET_SHARE——同一組維度可有多個來源並存，"
            + "來源是唯一鍵的一部分；來源明細為空的那筆填 null 即可")
    private String sourceDetail;
}
