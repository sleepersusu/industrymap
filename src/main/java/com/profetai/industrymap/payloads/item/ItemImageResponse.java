package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.ItemImage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一張圖片與它的熱區（design D6）：前端畫一張可互動的圖，圖與熱區缺一不可，
 * 因此巢狀在同一筆回應內，不讓呼叫端逐張再查一次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "品類節點的圖片，含其熱區")
public class ItemImageResponse {

    @Schema(description = "圖片 id")
    private Long id;

    @Schema(description = "所屬品類節點 id")
    private Long itemId;

    @Schema(description = "視角標籤，同一節點內唯一", example = "爆炸圖")
    private String viewLabel;

    @Schema(description = "物件儲存的 key 或 URL")
    private String storageKey;

    @Schema(description = "原圖寬（像素），可能為 null")
    private Integer widthPx;

    @Schema(description = "原圖高（像素），可能為 null")
    private Integer heightPx;

    @Schema(description = "審核狀態")
    private ReviewStatus reviewStatus;

    @Schema(description = "這張圖上的熱區；無熱區時為空陣列")
    private List<HotspotResponse> hotspots;

    /** 剛建立的圖片還沒有任何熱區，回空陣列而非 null——呼叫端不必為兩種形狀各寫一次處理 */
    public static ItemImageResponse from(ItemImage image) {
        return from(image, List.of());
    }

    public static ItemImageResponse from(ItemImage image, List<HotspotResponse> hotspots) {
        return ItemImageResponse.builder()
                .id(image.getId())
                .itemId(image.getItem().getId())
                .viewLabel(image.getViewLabel())
                .storageKey(image.getStorageKey())
                .widthPx(image.getWidthPx())
                .heightPx(image.getHeightPx())
                .reviewStatus(image.getReviewStatus())
                .hotspots(hotspots)
                .build();
    }
}
