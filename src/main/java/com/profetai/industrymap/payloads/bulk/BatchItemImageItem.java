package com.profetai.industrymap.payloads.bulk;

import com.profetai.industrymap.payloads.ProvenanceRequest;
import com.profetai.industrymap.payloads.item.CreateItemImageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批次建立節點圖片的單筆項目。
 *
 * <p>單筆端點把節點放在路徑上（{@code /api/items/{id}/images}），
 * 但一次匯入時各筆的節點各不相同，因此改成隨項目一起帶（同 {@link BatchCompositionItem}）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批次建立節點圖片的單筆項目")
public class BatchItemImageItem {

    @NotNull(message = "品類節點為必填")
    @Schema(description = "圖片所屬的 item id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    @NotBlank(message = "視角標籤為必填")
    @Size(max = 64, message = "視角標籤不得超過 64 字")
    @Schema(description = "視角標籤，同一節點內唯一", example = "爆炸圖",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String viewLabel;

    @NotBlank(message = "圖片位置為必填")
    @Size(max = 1024, message = "圖片位置不得超過 1024 字")
    @Schema(description = "物件儲存的 key 或 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String storageKey;

    @Positive(message = "原圖寬必須大於 0")
    @Schema(description = "原圖寬（像素）")
    private Integer widthPx;

    @Positive(message = "原圖高必須大於 0")
    @Schema(description = "原圖高（像素）")
    private Integer heightPx;

    @Valid
    @NotNull(message = "來源資訊為必填")
    private ProvenanceRequest provenance;

    /** 轉成單筆建立請求，讓批次與單筆走同一組 service 驗證（重複檢查、來源驗證） */
    public CreateItemImageRequest toRequest() {
        return CreateItemImageRequest.builder()
                .viewLabel(viewLabel)
                .storageKey(storageKey)
                .widthPx(widthPx)
                .heightPx(heightPx)
                .provenance(provenance)
                .build();
    }
}
