package com.profetai.industrymap.payloads.bulk;

import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.CreateCompositionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批次建立組成關係的單筆項目。
 *
 * <p>單筆端點把上層節點放在路徑上（{@code /api/items/{id}/components}），
 * 但一次匯入一台腳踏車時各筆的上層節點各不相同，因此改成隨項目一起帶。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批次建立組成關係的單筆項目")
public class BatchCompositionItem {

    @NotNull(message = "上層節點為必填")
    @Schema(description = "上層節點的 item id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentItemId;

    @NotNull(message = "下層零件為必填")
    @Schema(description = "下層零件的 item id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childItemId;

    @NotNull(message = "必要性為必填")
    @Schema(description = "必要性：標配 / 常見 / 選配", requiredMode = Schema.RequiredMode.REQUIRED)
    private Necessity necessity;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;

    /** 轉成單筆建立請求，讓批次與單筆走同一組 service 驗證（循環偵測、重複檢查） */
    public CreateCompositionRequest toRequest() {
        return CreateCompositionRequest.builder()
                .childItemId(childItemId)
                .necessity(necessity)
                .provenance(provenance)
                .build();
    }
}
