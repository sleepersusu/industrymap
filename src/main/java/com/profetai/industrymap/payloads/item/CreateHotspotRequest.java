package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.payloads.ProvenanceRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 在一張圖上標記一個熱區（design D2、D5）。
 *
 * <p>位置標籤是必填而非選填：同一張圖上可以有多個熱區指向同一個節點（前煞車／後煞車），
 * 標籤是它們唯一的區分方式，也是自然鍵的一半。允許為空等同唯一鍵失效。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "建立圖片熱區")
public class CreateHotspotRequest {

    @NotNull(message = "圖片 id 為必填")
    @Schema(description = "所屬圖片 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemImageId;

    @NotNull(message = "熱區指向的節點 id 為必填")
    @Schema(description = "這塊區域對應的品類節點 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childItemId;

    @NotBlank(message = "位置標籤為必填")
    @Size(max = 64, message = "位置標籤不得超過 64 字")
    @Schema(description = "位置標籤，同一張圖內唯一", example = "前煞車",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String positionLabel;

    @Valid
    @NotNull(message = "座標為必填")
    @Size(min = 3, message = "多邊形至少需要三個點")
    @Schema(description = "多邊形頂點集，至少三點", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<HotspotPointPayload> polygon;

    @Valid
    @NotNull(message = "來源資訊為必填")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ProvenanceRequest provenance;
}
