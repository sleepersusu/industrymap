package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "建立公司與零件供應關係的請求")
public class CreateCompanyItemRoleRequest {

    @NotNull(message = "公司為必填")
    @Schema(description = "公司 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long companyId;

    @NotNull(message = "零件為必填")
    @Schema(description = "零件 item id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @NotNull(message = "角色為必填")
    @Schema(description = "角色：設計 / 製造 / 代工組裝 / 品牌 / 封測", requiredMode = Schema.RequiredMode.REQUIRED)
    private CompanyRole companyRole;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;
}
