package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.payloads.ProvenanceRequest;
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
 * 為品類節點掛一張圖（design D4）。
 *
 * <p>只收物件儲存的位置，本次沒有上傳端點；圖片的二進位不進資料庫。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "建立品類節點圖片")
public class CreateItemImageRequest {

    @NotBlank(message = "視角標籤為必填")
    @Size(max = 64, message = "視角標籤不得超過 64 字")
    @Schema(description = "視角標籤，同一節點內唯一", example = "爆炸圖",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String viewLabel;

    @NotBlank(message = "圖片位置為必填")
    @Size(max = 1024, message = "圖片位置不得超過 1024 字")
    @Schema(description = "物件儲存的 key 或 URL", example = "https://cdn.example.com/bike-explosion.png",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String storageKey;

    @Positive(message = "原圖寬必須大於 0")
    @Schema(description = "原圖寬（像素），供前端估算版面；座標本身是相對比例，與此無關", example = "1200")
    private Integer widthPx;

    @Positive(message = "原圖高必須大於 0")
    @Schema(description = "原圖高（像素），用途同 widthPx", example = "800")
    private Integer heightPx;

    @Valid
    @NotNull(message = "來源資訊為必填")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ProvenanceRequest provenance;
}
