package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.CompanyItemRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "零件的供應公司與其角色")
public class SupplierResponse {

    @Schema(description = "公司顯示名稱")
    private String companyName;

    @Schema(description = "公司的對外識別：主要代號的交易所限定形式 <類型>:<代號值>，"
            + "無識別碼的公司則為正規化名稱", example = "TWSE:2330")
    private String companyReference;

    @Schema(description = "角色")
    private CompanyRole companyRole;

    @Schema(description = "審核狀態；納入草稿查詢時據此區分資料成熟度")
    private ReviewStatus reviewStatus;

    @Schema(description = "來源類型")
    private SourceType sourceType;

    /**
     * @param companyReference 公司對外識別，由 {@code CompanyReferences} 統一組出（design D4）；
     *                         刻意由呼叫端傳入而非在此查詢，避免每筆供應商各查一次識別碼
     */
    public static SupplierResponse from(CompanyItemRole role, String companyReference) {
        return SupplierResponse.builder()
                .companyName(role.getCompany().getDisplayName())
                .companyReference(companyReference)
                .companyRole(role.getCompanyRole())
                .reviewStatus(role.getReviewStatus())
                .sourceType(role.getSourceType())
                .build();
    }
}
