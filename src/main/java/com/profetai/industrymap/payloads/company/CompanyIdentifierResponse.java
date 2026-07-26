package com.profetai.industrymap.payloads.company;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.model.CompanyIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司識別碼")
public class CompanyIdentifierResponse {

    @Schema(description = "識別碼類型")
    private IdentifierType identifierType;

    @Schema(description = "識別碼值", example = "2330")
    private String identifierValue;

    @Schema(description = "是否為主要識別碼")
    private boolean primary;

    public static CompanyIdentifierResponse from(CompanyIdentifier identifier) {
        return CompanyIdentifierResponse.builder()
                .identifierType(identifier.getIdentifierType())
                .identifierValue(identifier.getIdentifierValue())
                .primary(identifier.isPrimary())
                .build();
    }
}
