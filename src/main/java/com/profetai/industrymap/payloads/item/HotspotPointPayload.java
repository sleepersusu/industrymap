package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.model.HotspotPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 熱區多邊形的一個頂點（design D5）：相對於圖片寬高的比例，0 至 1，含端點。
 *
 * <p>請求與回應共用同一個結構：兩個方向的形狀完全相同，各定義一份只會多一組要同步維護的欄位。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "熱區多邊形的頂點，座標為 0–1 的相對比例")
public class HotspotPointPayload {

    @NotNull(message = "座標 x 為必填")
    @DecimalMin(value = "0.0", message = "座標 x 不得小於 0")
    @DecimalMax(value = "1.0", message = "座標 x 不得大於 1")
    @Schema(description = "相對 X 座標，0 為圖片最左、1 為最右", example = "0.42",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double x;

    @NotNull(message = "座標 y 為必填")
    @DecimalMin(value = "0.0", message = "座標 y 不得小於 0")
    @DecimalMax(value = "1.0", message = "座標 y 不得大於 1")
    @Schema(description = "相對 Y 座標，0 為圖片最上、1 為最下", example = "0.31",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Double y;

    public static HotspotPointPayload from(HotspotPoint point) {
        return HotspotPointPayload.builder().x(point.getX()).y(point.getY()).build();
    }
}
