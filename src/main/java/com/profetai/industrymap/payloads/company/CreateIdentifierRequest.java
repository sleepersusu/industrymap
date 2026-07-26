package com.profetai.industrymap.payloads.company;

import com.profetai.industrymap.enums.IdentifierType;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登記公司識別碼的請求")
public class CreateIdentifierRequest {

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
}
