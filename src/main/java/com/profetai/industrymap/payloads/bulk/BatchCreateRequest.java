package com.profetai.industrymap.payloads.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批次建立請求。各類型的項目格式不同，但「一次送多筆」這件事完全一樣，
 * 因此以泛型共用同一個外殼，不必每個資源各定義一個幾乎相同的 wrapper。
 *
 * <p>空批次由 {@code @NotEmpty} 在 API 邊界擋下，不進 service。</p>
 *
 * @param <T> 該資源的單筆建立請求型別
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批次建立請求")
public class BatchCreateRequest<T> {

    @Valid
    @NotEmpty(message = "批次項目不得為空")
    @Schema(description = "要建立的項目清單", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> items;
}
