package com.profetai.industrymap.payloads.item;

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
@Schema(description = "登記品類節點別名的請求")
public class CreateItemAliasRequest {

    @NotBlank(message = "別名為必填")
    @Schema(description = "別名，例：無線網卡", example = "無線網卡")
    private String alias;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;
}
