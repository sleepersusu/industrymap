package com.profetai.industrymap.payloads.bulk;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.company.CreateIdentifierRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批次登記公司識別碼的單筆項目。單筆端點把公司放在路徑上
 * （{@code /api/companies/{code}/identifiers}），批次則各筆自帶公司代號。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批次登記公司識別碼的單筆項目")
public class BatchIdentifierItem {

    @NotBlank(message = "公司代號為必填")
    @Schema(description = "公司對外識別；未上市公司用正規化名稱",
            example = "台積電", requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyCode;

    @NotNull(message = "識別碼類型為必填")
    @Schema(description = "識別碼類型", requiredMode = Schema.RequiredMode.REQUIRED)
    private IdentifierType identifierType;

    @NotBlank(message = "識別碼值為必填")
    @Schema(description = "識別碼值", example = "2330", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifierValue;

    @Schema(description = "是否為主要識別碼；每家公司至多一筆", example = "true")
    private boolean primary;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;

    /** 轉成單筆建立請求，讓批次與單筆走同一組 service 驗證（重複、主要識別碼唯一） */
    public CreateIdentifierRequest toRequest() {
        return CreateIdentifierRequest.builder()
                .identifierType(identifierType)
                .identifierValue(identifierValue)
                .primary(primary)
                .provenance(provenance)
                .build();
    }
}
