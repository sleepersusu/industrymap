package com.profetai.industrymap.payloads.supply;

import com.profetai.industrymap.enums.CompanyRole;
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
@Schema(description = "建立公司與零件供應關係的請求")
public class CreateCompanyItemRoleRequest {

    @NotBlank(message = "公司代號為必填")
    @Schema(description = "公司對外識別，建議用交易所限定形式 <類型>:<代號值>；未上市公司用正規化名稱。"
            + "與公司回應的 reference 一致。裸代號仍可用，但同代號值跨交易所撞號時回 409",
            example = "TWSE:5306", requiredMode = Schema.RequiredMode.REQUIRED)
    private String companyCode;

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
