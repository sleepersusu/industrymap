package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.enums.Necessity;
import com.profetai.industrymap.enums.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 組成樹的單一節點。深度由呼叫端指定，避免一次拉出整張圖（風險段落已載明）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "組成樹節點")
public class ComponentNode {

    @Schema(description = "品類節點 id")
    private Long itemId;

    @Schema(description = "顯示用名稱")
    private String displayName;

    @Schema(description = "是否為終端成品")
    private boolean endProduct;

    @Schema(description = "相對於上層節點的必要性；根節點為 null")
    private Necessity necessity;

    @Schema(description = "該筆組成關係的審核狀態；根節點為節點自身的審核狀態")
    private ReviewStatus reviewStatus;

    @Schema(description = "下層組成零件；已達指定深度時為空清單")
    private List<ComponentNode> children;
}
