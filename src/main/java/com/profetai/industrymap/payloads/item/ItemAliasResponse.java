package com.profetai.industrymap.payloads.item;

import com.profetai.industrymap.model.ItemAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "品類節點別名")
public class ItemAliasResponse {

    @Schema(description = "別名 id")
    private Long id;

    @Schema(description = "所屬節點 id")
    private Long itemId;

    @Schema(description = "別名原始寫法")
    private String alias;

    @Schema(description = "正規化別名")
    private String normalizedAlias;

    public static ItemAliasResponse from(ItemAlias alias) {
        return ItemAliasResponse.builder()
                .id(alias.getId())
                .itemId(alias.getItem() == null ? null : alias.getItem().getId())
                .alias(alias.getDisplayAlias())
                .normalizedAlias(alias.getNormalizedAlias())
                .build();
    }
}
