package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.enums.SourceType;
import com.profetai.industrymap.model.Item;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "品類節點")
public class ItemResponse {

    @Schema(description = "節點 id")
    private Long id;

    @Schema(description = "顯示用名稱")
    private String displayName;

    @Schema(description = "正規化名稱，全站唯一")
    private String normalizedName;

    @Schema(description = "是否為終端成品")
    private boolean endProduct;

    @Schema(description = "is-a 上層品類 id")
    private Long parentCategoryId;

    @Schema(description = "審核狀態")
    private ReviewStatus reviewStatus;

    @Schema(description = "來源類型")
    private SourceType sourceType;

    public static ItemResponse from(Item item) {
        return ItemResponse.builder()
                .id(item.getId())
                .displayName(item.getDisplayName())
                .normalizedName(item.getNormalizedName())
                .endProduct(item.isEndProduct())
                .parentCategoryId(item.getParentCategory() == null ? null : item.getParentCategory().getId())
                .reviewStatus(item.getReviewStatus())
                .sourceType(item.getSourceType())
                .build();
    }
}
