package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.Necessity;
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
@Schema(description = "建立 part-of 組成關係的請求")
public class CreateCompositionRequest {

    @NotNull(message = "下層零件為必填")
    @Schema(description = "下層零件的 item id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childItemId;

    @NotNull(message = "必要性為必填")
    @Schema(description = "必要性：標配 / 常見 / 選配", requiredMode = Schema.RequiredMode.REQUIRED)
    private Necessity necessity;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;
}
