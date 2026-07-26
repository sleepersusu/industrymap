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
@Schema(description = "建立品類節點的請求")
public class CreateItemRequest {

    @NotBlank(message = "名稱為必填")
    @Schema(description = "顯示用名稱，系統會另存正規化名稱作為唯一鍵", example = "WiFi 模組")
    private String displayName;

    @Schema(description = "是否為終端成品", example = "false")
    private boolean endProduct;

    @Schema(description = "is-a 上層品類 id；例：車用 PCB 指向 PCB")
    private Long parentCategoryId;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;
}
