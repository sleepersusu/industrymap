package com.profetai.industrymap.payloads.company;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.helper.CompanyReferences;
import com.profetai.industrymap.model.Company;
import com.profetai.industrymap.model.CompanyIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 公司資料。對外不曝露內部自增主鍵，改以 {@code reference}（主要代號，沒有代號時為正規化名稱）
 * 作為後續 API 的路徑識別。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司資料")
public class CompanyResponse {

    @Schema(description = "後續 API 使用的路徑識別：主要代號，未上市公司則為正規化名稱", example = "2330")
    private String reference;

    @Schema(description = "顯示用公司名稱")
    private String displayName;

    @Schema(description = "正規化名稱")
    private String normalizedName;

    @Schema(description = "所屬國家")
    private String country;

    @Schema(description = "是否為公開發行公司")
    private boolean publicCompany;

    @Schema(description = "審核狀態")
    private ReviewStatus reviewStatus;

    @Schema(description = "該公司的所有識別碼")
    private List<CompanyIdentifierResponse> identifiers;

    public static CompanyResponse from(Company company, List<CompanyIdentifier> identifiers) {
        return CompanyResponse.builder()
                .reference(CompanyReferences.of(company, identifiers))
                .displayName(company.getDisplayName())
                .normalizedName(company.getNormalizedName())
                .country(company.getCountry())
                .publicCompany(company.isPublic())
                .reviewStatus(company.getReviewStatus())
                .identifiers(identifiers.stream().map(CompanyIdentifierResponse::from).toList())
                .build();
    }
}
