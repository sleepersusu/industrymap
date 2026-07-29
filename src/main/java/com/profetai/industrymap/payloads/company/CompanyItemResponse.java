package com.profetai.industrymap.payloads.company;

import com.profetai.industrymap.enums.CompanyRole;
import com.profetai.industrymap.enums.ReviewStatus;
import com.profetai.industrymap.model.Item;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 公司供應的單一品類節點。
 *
 * <p>以節點為單位而非以「節點 × 角色」為單位：這個端點回答的是「這家公司做哪些零件」，
 * 答案的單位就是零件。一家公司對同一顆晶片可以同時是製造與封測，若讓它出現兩次，
 * 等於把去重的責任丟給每一個呼叫端。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司供應的品類節點")
public class CompanyItemResponse {

    @Schema(description = "品類節點 id，可用於展開組成樹或查其他供應商")
    private Long itemId;

    @Schema(description = "顯示用名稱")
    private String displayName;

    @Schema(description = "是否為終端成品")
    private boolean endProduct;

    @Schema(description = "該公司對這個節點擔任的所有角色")
    private List<CompanyRole> roles;

    @Schema(description = "節點自身的審核狀態；供應角色的狀態不在這裡，一個節點可能有多筆角色")
    private ReviewStatus reviewStatus;

    public static CompanyItemResponse from(Item item, List<CompanyRole> roles) {
        return CompanyItemResponse.builder()
                .itemId(item.getId())
                .displayName(item.getDisplayName())
                .endProduct(item.isEndProduct())
                .roles(roles)
                .reviewStatus(item.getReviewStatus())
                .build();
    }
}
