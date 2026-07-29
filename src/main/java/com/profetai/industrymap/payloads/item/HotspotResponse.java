package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.ItemHotspot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "圖片上的可點擊區域")
public class HotspotResponse {

    @Schema(description = "熱區 id")
    private Long id;

    @Schema(description = "所屬圖片 id")
    private Long itemImageId;

    @Schema(description = "這塊區域對應的品類節點 id；點擊後即由此往下查供應公司")
    private Long childItemId;

    @Schema(description = "對應節點的顯示名稱，供前端直接標示，不必再逐筆查節點")
    private String childDisplayName;

    @Schema(description = "位置標籤，同一張圖內唯一", example = "前煞車")
    private String positionLabel;

    @Schema(description = "多邊形頂點集，座標為 0–1 的相對比例")
    private List<HotspotPointPayload> polygon;

    @Schema(description = "審核狀態")
    private ReviewStatus reviewStatus;

    /**
     * 組裝必須在交易內：{@code itemImage} 與 {@code childItem} 都是 LAZY 關聯，
     * 而 open-in-view 為 false。
     */
    public static HotspotResponse from(ItemHotspot hotspot) {
        return HotspotResponse.builder()
                .id(hotspot.getId())
                .itemImageId(hotspot.getItemImage().getId())
                .childItemId(hotspot.getChildItem().getId())
                .childDisplayName(hotspot.getChildItem().getDisplayName())
                .positionLabel(hotspot.getPositionLabel())
                .polygon(hotspot.getPolygon().stream().map(HotspotPointPayload::from).toList())
                .reviewStatus(hotspot.getReviewStatus())
                .build();
    }
}
