package com.profetai.industrymap.payloads.item;

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
 * 修正熱區：全量替換位置標籤與座標（design D7）。
 *
 * <p>刻意不收「指向哪個節點」：改指向等於這是另一個熱區，該走建立與駁回，
 * 而不是把一筆已審過的資料原地改成指向別的東西。</p>
 *
 * <p>部分更新沒有意義——座標是一整組點，替換其中幾個點無法表達；
 * 位置標籤與座標一併帶齊才知道呼叫端要的最終狀態是什麼。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "修正熱區（全量替換）")
public class AmendHotspotRequest {

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
}
