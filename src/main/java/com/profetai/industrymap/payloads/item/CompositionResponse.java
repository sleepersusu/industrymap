package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.ItemComposition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "part-of 組成關係")
public class CompositionResponse {

    @Schema(description = "組成關係 id")
    private Long id;

    @Schema(description = "上層節點 id")
    private Long parentItemId;

    @Schema(description = "下層零件 id")
    private Long childItemId;

    @Schema(description = "必要性")
    private Necessity necessity;

    @Schema(description = "審核狀態")
    private ReviewStatus reviewStatus;

    public static CompositionResponse from(ItemComposition composition) {
        return CompositionResponse.builder()
                .id(composition.getId())
                .parentItemId(composition.getParentItem().getId())
                .childItemId(composition.getChildItem().getId())
                .necessity(composition.getNecessity())
                .reviewStatus(composition.getReviewStatus())
                .build();
    }
}
