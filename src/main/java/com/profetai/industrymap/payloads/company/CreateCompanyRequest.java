package com.profetai.industrymap.payloads.company;

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
@Schema(description = "建立公司的請求")
public class CreateCompanyRequest {

    @NotBlank(message = "公司名稱為必填")
    @Schema(description = "顯示用公司名稱", example = "台積電")
    private String displayName;

    @Schema(description = "所屬國家", example = "TW")
    private String country;

    @Schema(description = "是否為公開發行公司；未上市公司填 false 亦可正常建立", example = "true")
    private boolean publicCompany;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;
}
